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

package fusion.http.gateway.server

import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.http.scaladsl.server.Route
import fusion.core.extension.FusionExtension

class FusionHttpGateway private (protected val _system: ExtendedActorSystem) extends FusionExtension {
  val components = new HttpGatewayComponents(_system)
  def component: Route = components.component
}

object FusionHttpGateway extends ExtensionId[FusionHttpGateway] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): FusionHttpGateway = new FusionHttpGateway(system)
  override def lookup(): ExtensionId[_ <: Extension] = FusionHttpGateway
}
