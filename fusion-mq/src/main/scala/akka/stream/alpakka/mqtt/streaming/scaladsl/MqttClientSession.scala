/*
 * Copyright 2019 akka-fusion.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akka.stream.alpakka.mqtt.streaming.scaladsl

import java.util.concurrent.atomic.AtomicLong

import akka.actor.ExtendedActorSystem
import akka.actor.typed.Props
import akka.actor.typed.internal.adapter.{ ActorRefAdapter, PropsAdapter }
import akka.actor.typed.scaladsl.adapter._
import akka.event.Logging
import akka.stream._
import akka.stream.alpakka.mqtt.streaming._
import akka.stream.alpakka.mqtt.streaming.impl._
import akka.stream.scaladsl.{ Flow, Source }
import akka.util.ByteString
import akka.{ Done, NotUsed, actor => untyped }

import scala.concurrent.{ Future, Promise }
import scala.util.control.NoStackTrace
import scala.util.{ Failure, Success }

/**
 * Represents client-only sessions
 */
abstract class MqttClientSession extends MqttSession {
  import MqttSession._

  /**
   * @return a flow for commands to be sent to the session
   */
  private[streaming] def commandFlow[A](connectionId: ByteString): CommandFlow[A]

  /**
   * @return a flow for events to be emitted by the session
   */
  private[streaming] def eventFlow[A](connectionId: ByteString): EventFlow[A]
}

object ActorMqttClientSession {
  def apply(
      settings: MqttSessionSettings)(implicit mat: Materializer, system: untyped.ActorSystem): ActorMqttClientSession =
    new ActorMqttClientSession(settings)

  /**
   * No ACK received - the CONNECT failed
   */
  case object ConnectFailed extends Exception with NoStackTrace

  /**
   * No ACK received - the SUBSCRIBE failed
   */
  case object SubscribeFailed extends Exception with NoStackTrace

  /**
   * A PINGREQ failed to receive a PINGRESP - the connection must close
   *
   * http://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html
   * 3.1.2.10 Keep Alive
   */
  case object PingFailed extends Exception with NoStackTrace

  private[scaladsl] val clientSessionCounter = new AtomicLong
}

/**
 * Provides an actor implementation of a client session
 * @param settings session settings
 */
final class ActorMqttClientSession(settings: MqttSessionSettings)(
    implicit mat: Materializer,
    system: untyped.ActorSystem)
    extends MqttClientSession {
  import ActorMqttClientSession._

  private val clientSessionId = clientSessionCounter.getAndIncrement()
  private val consumerPacketRouter =
    ActorRefAdapter(
      system
        .asInstanceOf[ExtendedActorSystem]
        .systemActorOf(
          PropsAdapter(() => RemotePacketRouter[Consumer.Event], Props.empty, false),
          "client-consumer-packet-id-allocator-" + clientSessionId))
  private val producerPacketRouter =
    ActorRefAdapter(
      system
        .asInstanceOf[ExtendedActorSystem]
        .systemActorOf(
          PropsAdapter(() => LocalPacketRouter[Producer.Event], Props.empty, false),
          "client-producer-packet-id-allocator-" + clientSessionId))
  private val subscriberPacketRouter =
    ActorRefAdapter(
      system
        .asInstanceOf[ExtendedActorSystem]
        .systemActorOf(
          PropsAdapter(() => LocalPacketRouter[Subscriber.Event], Props.empty, false),
          "client-subscriber-packet-id-allocator-" + clientSessionId))
  private val unsubscriberPacketRouter =
    ActorRefAdapter(
      system
        .asInstanceOf[ExtendedActorSystem]
        .systemActorOf(
          PropsAdapter(() => LocalPacketRouter[Unsubscriber.Event], Props.empty, false),
          "client-unsubscriber-packet-id-allocator-" + clientSessionId))
  private val clientConnector =
    ActorRefAdapter(
      system
        .asInstanceOf[ExtendedActorSystem]
        .systemActorOf(
          PropsAdapter(
            () =>
              ClientConnector(
                consumerPacketRouter,
                producerPacketRouter,
                subscriberPacketRouter,
                unsubscriberPacketRouter,
                settings),
            Props.empty,
            false),
          "client-connector-" + clientSessionId))

  import MqttSession._
  import akka.stream.alpakka.mqtt.streaming.MqttCodec._
  import system.dispatcher

  override def ![A](cp: Command[A]): Unit = cp match {
    case Command(cp: Publish, _, carry) =>
      clientConnector ! ClientConnector.PublishReceivedLocally(cp, carry)
    case c: Command[A] => throw new IllegalStateException(s"$c is not a client command that can be sent directly")
  }

  override def shutdown(): Unit = {
    system.stop(clientConnector.toClassic)
    system.stop(consumerPacketRouter.toClassic)
    system.stop(producerPacketRouter.toClassic)
    system.stop(subscriberPacketRouter.toClassic)
    system.stop(unsubscriberPacketRouter.toClassic)
  }

  private val pingReqBytes = PingReq.encode(ByteString.newBuilder).result()

  private[streaming] override def commandFlow[A](connectionId: ByteString): CommandFlow[A] =
    Flow
      .lazyFutureFlow { () =>
        val killSwitch = KillSwitches.shared("command-kill-switch-" + clientSessionId)

        Future.successful(
          Flow[Command[A]]
            .watch(clientConnector.toClassic)
            .watchTermination() {
              case (_, terminated) =>
                terminated.onComplete {
                  case Failure(_: WatchedActorTerminatedException) =>
                  case _ =>
                    clientConnector ! ClientConnector.ConnectionLost(connectionId)
                }
                NotUsed
            }
            .via(killSwitch.flow)
            .flatMapMerge(
              settings.commandParallelism, {
                case Command(cp: Connect, _, carry) =>
                  val reply = Promise[Source[ClientConnector.ForwardConnectCommand, NotUsed]]
                  clientConnector ! ClientConnector.ConnectReceivedLocally(connectionId, cp, carry, reply)
                  Source.futureSource(reply.future.map(_.map {
                    case ClientConnector.ForwardConnect => cp.encode(ByteString.newBuilder).result()
                    case ClientConnector.ForwardPingReq => pingReqBytes
                    case ClientConnector.ForwardPublish(publish, packetId) =>
                      publish.encode(ByteString.newBuilder, packetId).result()
                    case ClientConnector.ForwardPubRel(packetId) =>
                      PubRel(packetId).encode(ByteString.newBuilder).result()
                  }.mapError {
                      case ClientConnector.ConnectFailed => ActorMqttClientSession.ConnectFailed
                      case Subscriber.SubscribeFailed    => ActorMqttClientSession.SubscribeFailed
                      case ClientConnector.PingFailed    => ActorMqttClientSession.PingFailed
                    }
                    .watchTermination() { (_, done) =>
                      done.onComplete {
                        case Success(_) => killSwitch.shutdown()
                        case Failure(t) => killSwitch.abort(t)
                      }
                    }))
                case Command(cp: PubAck, completed, _) =>
                  val reply = Promise[Consumer.ForwardPubAck.type]
                  consumerPacketRouter ! RemotePacketRouter
                    .Route(None, cp.packetId, Consumer.PubAckReceivedLocally(reply), reply)

                  reply.future.onComplete { result =>
                    completed.foreach(_.complete(result.map(_ => Done)))
                  }

                  Source.future(reply.future.map(_ => cp.encode(ByteString.newBuilder).result())).recover {
                    case _: RemotePacketRouter.CannotRoute => ByteString.empty
                  }
                case Command(cp: PubRec, completed, _) =>
                  val reply = Promise[Consumer.ForwardPubRec.type]
                  consumerPacketRouter ! RemotePacketRouter
                    .Route(None, cp.packetId, Consumer.PubRecReceivedLocally(reply), reply)

                  reply.future.onComplete { result =>
                    completed.foreach(_.complete(result.map(_ => Done)))
                  }

                  Source.future(reply.future.map(_ => cp.encode(ByteString.newBuilder).result())).recover {
                    case _: RemotePacketRouter.CannotRoute => ByteString.empty
                  }
                case Command(cp: PubComp, completed, _) =>
                  val reply = Promise[Consumer.ForwardPubComp.type]
                  consumerPacketRouter ! RemotePacketRouter
                    .Route(None, cp.packetId, Consumer.PubCompReceivedLocally(reply), reply)

                  reply.future.onComplete { result =>
                    completed.foreach(_.complete(result.map(_ => Done)))
                  }

                  Source.future(reply.future.map(_ => cp.encode(ByteString.newBuilder).result())).recover {
                    case _: RemotePacketRouter.CannotRoute => ByteString.empty
                  }
                case Command(cp: Subscribe, _, carry) =>
                  val reply = Promise[Subscriber.ForwardSubscribe]
                  clientConnector ! ClientConnector.SubscribeReceivedLocally(connectionId, cp, carry, reply)
                  Source.future(
                    reply.future.map(command => cp.encode(ByteString.newBuilder, command.packetId).result()))
                case Command(cp: Unsubscribe, _, carry) =>
                  val reply = Promise[Unsubscriber.ForwardUnsubscribe]
                  clientConnector ! ClientConnector.UnsubscribeReceivedLocally(connectionId, cp, carry, reply)
                  Source.future(
                    reply.future.map(command => cp.encode(ByteString.newBuilder, command.packetId).result()))
                case Command(cp: Disconnect.type, _, _) =>
                  val reply = Promise[ClientConnector.ForwardDisconnect.type]
                  clientConnector ! ClientConnector.DisconnectReceivedLocally(connectionId, reply)
                  Source.future(reply.future.map(_ => cp.encode(ByteString.newBuilder).result()))
                case c: Command[A] => throw new IllegalStateException(s"$c is not a client command")
              })
            .recover {
              case _: WatchedActorTerminatedException => ByteString.empty
            }
            .filter(_.nonEmpty)
            .log("client-commandFlow", _.iterator.decodeControlPacket(settings.maxPacketSize)) // we decode here so we can see the generated packet id
            .withAttributes(ActorAttributes.logLevels(onFailure = Logging.DebugLevel)))
      }
      .mapMaterializedValue(_ => NotUsed)

  private[streaming] override def eventFlow[A](connectionId: ByteString): EventFlow[A] =
    Flow[ByteString]
      .watch(clientConnector.toClassic)
      .watchTermination() {
        case (_, terminated) =>
          terminated.onComplete {
            case Failure(_: WatchedActorTerminatedException) =>
            case _ =>
              clientConnector ! ClientConnector.ConnectionLost(connectionId)
          }
          NotUsed
      }
      .via(new MqttFrameStage(settings.maxPacketSize))
      .map(_.iterator.decodeControlPacket(settings.maxPacketSize))
      .log("client-events")
      .mapAsync[Either[MqttCodec.DecodeError, Event[A]]](settings.eventParallelism) {
        case Right(cp: ConnAck) =>
          val reply = Promise[ClientConnector.ForwardConnAck]
          clientConnector ! ClientConnector.ConnAckReceivedFromRemote(connectionId, cp, reply)
          reply.future.map {
            case ClientConnector.ForwardConnAck(carry: Option[A] @unchecked) => Right(Event(cp, carry))
          }
        case Right(cp: SubAck) =>
          val reply = Promise[Subscriber.ForwardSubAck]
          subscriberPacketRouter ! LocalPacketRouter.Route(
            cp.packetId,
            Subscriber.SubAckReceivedFromRemote(reply),
            reply)
          reply.future.map {
            case Subscriber.ForwardSubAck(carry: Option[A] @unchecked) => Right(Event(cp, carry))
          }
        case Right(cp: UnsubAck) =>
          val reply = Promise[Unsubscriber.ForwardUnsubAck]
          unsubscriberPacketRouter ! LocalPacketRouter.Route(
            cp.packetId,
            Unsubscriber.UnsubAckReceivedFromRemote(reply),
            reply)
          reply.future.map {
            case Unsubscriber.ForwardUnsubAck(carry: Option[A] @unchecked) => Right(Event(cp, carry))
          }
        case Right(cp: Publish) =>
          val reply = Promise[Consumer.ForwardPublish.type]
          clientConnector ! ClientConnector.PublishReceivedFromRemote(connectionId, cp, reply)
          reply.future.map(_ => Right(Event(cp)))
        case Right(cp: PubAck) =>
          val reply = Promise[Producer.ForwardPubAck]
          producerPacketRouter ! LocalPacketRouter.Route(cp.packetId, Producer.PubAckReceivedFromRemote(reply), reply)
          reply.future.map {
            case Producer.ForwardPubAck(carry: Option[A] @unchecked) => Right(Event(cp, carry))
          }
        case Right(cp: PubRec) =>
          val reply = Promise[Producer.ForwardPubRec]
          producerPacketRouter ! LocalPacketRouter.Route(cp.packetId, Producer.PubRecReceivedFromRemote(reply), reply)
          reply.future.map {
            case Producer.ForwardPubRec(carry: Option[A] @unchecked) => Right(Event(cp, carry))
          }
        case Right(cp: PubRel) =>
          val reply = Promise[Consumer.ForwardPubRel.type]
          consumerPacketRouter ! RemotePacketRouter.Route(
            None,
            cp.packetId,
            Consumer.PubRelReceivedFromRemote(reply),
            reply)
          reply.future.map(_ => Right(Event(cp)))
        case Right(cp: PubComp) =>
          val reply = Promise[Producer.ForwardPubComp]
          producerPacketRouter ! LocalPacketRouter.Route(cp.packetId, Producer.PubCompReceivedFromRemote(reply), reply)
          reply.future.map {
            case Producer.ForwardPubComp(carry: Option[A] @unchecked) => Right(Event(cp, carry))
          }
        case Right(PingResp) =>
          val reply = Promise[ClientConnector.ForwardPingResp.type]
          clientConnector ! ClientConnector.PingRespReceivedFromRemote(connectionId, reply)
          reply.future.map(_ => Right(Event(PingResp)))
        case Right(cp) => Future.failed(new IllegalStateException(s"$cp is not a client event"))
        case Left(de)  => Future.successful(Left(de))
      }
      .withAttributes(ActorAttributes.supervisionStrategy {
        // Benign exceptions
        case _: LocalPacketRouter.CannotRoute | _: RemotePacketRouter.CannotRoute =>
          Supervision.Resume
        case _ =>
          Supervision.Stop
      })
      .recoverWithRetries(-1, {
        case _: WatchedActorTerminatedException => Source.empty
      })
      .withAttributes(ActorAttributes.logLevels(onFailure = Logging.DebugLevel))
}