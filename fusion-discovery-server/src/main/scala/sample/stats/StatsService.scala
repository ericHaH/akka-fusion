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

package sample.stats

import scala.concurrent.duration._
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import fusion.json.jackson.CborSerializable

//#service
object StatsService {
  sealed trait Command extends CborSerializable

  final case class ProcessText(text: String, replyTo: ActorRef[Response]) extends Command {
    require(text.nonEmpty)
  }
  case object Stop extends Command

  sealed trait Response extends CborSerializable
  final case class JobResult(meanWordLength: Double) extends Response
  final case class JobFailed(reason: String) extends Response

  def apply(workers: ActorRef[StatsWorker.Process]): Behavior[Command] =
    Behaviors.setup { ctx =>
      // if all workers would crash/stop we want to stop as well
      ctx.watch(workers)

      Behaviors.receiveMessage {
        case ProcessText(text, replyTo) =>
          ctx.log.info("Delegating request")
          val words = text.split(' ').toIndexedSeq
          // create per request actor that collects replies from workers
          ctx.spawnAnonymous(StatsAggregator(words, workers, replyTo))
          Behaviors.same
        case Stop =>
          Behaviors.stopped
      }
    }
}

object StatsAggregator {
  sealed trait Event
  private case object Timeout extends Event
  private case class CalculationComplete(length: Int) extends Event

  def apply(
      words: Seq[String],
      workers: ActorRef[StatsWorker.Process],
      replyTo: ActorRef[StatsService.Response]): Behavior[Event] =
    Behaviors.setup { ctx =>
      ctx.setReceiveTimeout(3.seconds, Timeout)
      val responseAdapter =
        ctx.messageAdapter[StatsWorker.Processed](processed => CalculationComplete(processed.length))

      words.foreach { word =>
        workers ! StatsWorker.Process(word, responseAdapter)
      }
      waiting(replyTo, words.size, Nil)
    }

  private def waiting(
      replyTo: ActorRef[StatsService.Response],
      expectedResponses: Int,
      results: List[Int]): Behavior[Event] =
    Behaviors.receiveMessage {
      case CalculationComplete(length) =>
        val newResults = results :+ length
        if (newResults.size == expectedResponses) {
          val meanWordLength = newResults.sum.toDouble / newResults.size
          replyTo ! StatsService.JobResult(meanWordLength)
          Behaviors.stopped
        } else {
          waiting(replyTo, expectedResponses, newResults)
        }
      case Timeout =>
        replyTo ! StatsService.JobFailed("Service unavailable, try again later")
        Behaviors.stopped
    }
}
//#service
