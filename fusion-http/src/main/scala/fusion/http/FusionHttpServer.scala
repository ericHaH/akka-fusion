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

package fusion.http

import akka.Done
import akka.actor.typed.ActorSystem
import fusion.common.component.Components
import fusion.common.extension.{ FusionCoordinatedShutdown, FusionExtension, FusionExtensionId }
import fusion.http.constant.HttpConstants
import helloscala.common.Configuration

import scala.concurrent.Future

private[http] class HttpServerComponents(system: ActorSystem[_])
    extends Components[HttpServer](HttpConstants.PATH_DEFAULT) {
  override val configuration: Configuration = Configuration(system.settings.config)
  override protected def createComponent(id: String): HttpServer = new HttpServer(id, system)
  override protected def componentClose(c: HttpServer): Future[Done] = c.closeAsync()
}

class FusionHttpServer private (override val system: ActorSystem[_]) extends FusionExtension {
  val components = new HttpServerComponents(system)
  def component: HttpServer = components.component
  FusionCoordinatedShutdown(system).serviceUnbind("StopFusionHttpServer") { () =>
    components.closeAsync()(system.executionContext)
  }
}

object FusionHttpServer extends FusionExtensionId[FusionHttpServer] {
  override def createExtension(system: ActorSystem[_]): FusionHttpServer = new FusionHttpServer(system)
}
