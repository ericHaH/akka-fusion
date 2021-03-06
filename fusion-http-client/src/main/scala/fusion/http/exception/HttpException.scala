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

package fusion.http.exception

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCode
import helloscala.common.IntStatus
import helloscala.common.exception.HSException

case class HttpException(
    statusCode: StatusCode,
    override val msg: String,
    override val data: Object = null,
    override val status: Int = IntStatus.INTERNAL_ERROR,
    override val cause: Throwable = null)
    extends HSException(status, msg, cause) {
  override val httpStatus: Int = statusCode.intValue()
}

case class HttpResponseException(httpResponse: HttpResponse) extends HSException(IntStatus.OK)
