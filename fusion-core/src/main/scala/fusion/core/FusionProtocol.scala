/*
 * Copyright 2019 helloscala.com
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

package fusion.core

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.Props
import akka.actor.typed.scaladsl.Behaviors

import scala.annotation.tailrec

object FusionProtocol {
  sealed trait Command {}

  object Spawn {

    /**
     * Special factory to make using Spawn with ask easier
     */
    def apply[T](behavior: Behavior[T], name: String, props: Props): ActorRef[ActorRef[T]] => Spawn[T] =
      replyTo => new Spawn(behavior, name, props, replyTo)

    /**
     * Special factory to make using Spawn with ask easier. Props defaults to Props.empty
     */
    def apply[T](behavior: Behavior[T], name: String): ActorRef[ActorRef[T]] => Spawn[T] =
      replyTo => new Spawn(behavior, name, Props.empty, replyTo)
  }

  /**
   * Spawn a child actor with the given `behavior` and send back the `ActorRef` of that child to the given
   * `replyTo` destination.
   *
   * If `name` is an empty string an anonymous actor (with automatically generated name) will be created.
   *
   * If the `name` is already taken of an existing actor a unique name will be used by appending a suffix
   * to the the `name`. The exact format or value of the suffix is an implementation detail that is
   * undefined. This means that reusing the same name for several actors will not result in
   * `InvalidActorNameException`, but it's better to use unique names to begin with.
   */
  final case class Spawn[T](behavior: Behavior[T], name: String, props: Props, replyTo: ActorRef[ActorRef[T]])
      extends Command

  final case object Shutdown extends Command

  /**
   * Behavior implementing the [[FusionProtocol.Command]].
   */
  val behavior: Behavior[Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case Spawn(bhvr, name, props, replyTo) =>
          val ref =
            if (name == null || name.equals(""))
              ctx.spawnAnonymous(bhvr, props)
            else {

              @tailrec def spawnWithUniqueName(c: Int): ActorRef[Any] = {
                val nameSuggestion = if (c == 0) name else s"$name-$c"
                ctx.child(nameSuggestion) match {
                  case Some(_) => spawnWithUniqueName(c + 1) // already taken, try next
                  case None    => ctx.spawn(bhvr, nameSuggestion, props)
                }
              }

              spawnWithUniqueName(0)
            }
          replyTo ! ref
          Behaviors.same

        case Shutdown =>
          Behaviors.stopped
      }
    }

}