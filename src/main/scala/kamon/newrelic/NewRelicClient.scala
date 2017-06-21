/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.newrelic

import scalaj.http.Http

trait NewRelicClient {
  protected def execute(url: String, payload: Array[Byte]): String

  def execute(url: String, method: String, payload: String): String = {
    //I don't why only "connect" method need to be deflated manually
    val mayBeDeflatePayload =
      if (method.equalsIgnoreCase("connect"))
        KamonDeflater.compress(payload.getBytes("UTF-8"))
      else
        payload.getBytes("UTF-8")

    execute(url, mayBeDeflatePayload)
  }
}

class ScalaJClient extends NewRelicClient {
  override protected def execute(url: String, payload: Array[Byte]): String = {
    Http(url)
      .postData(payload)
      .header("Content-Type", "application/json")
      .header("Accept-Encoding", "deflate")
      .compress(true)
      .asString
      .body
  }
}
