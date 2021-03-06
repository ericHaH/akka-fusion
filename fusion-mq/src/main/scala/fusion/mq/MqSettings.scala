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

package fusion.mq

import helloscala.common.Configuration

case class MqServerSettings(host: String, port: Int, maxConnections: Int)
case class MqSettings(server: MqServerSettings)

object MqSettings {
  def apply(configuration: Configuration, prefix: String = "fusion.mq"): MqSettings = {
    val c = configuration.getConfiguration(prefix)
    MqSettings(
      MqServerSettings(c.getString("server.host"), c.getInt("server.port"), c.getInt("server.max-connections")))
  }
}
