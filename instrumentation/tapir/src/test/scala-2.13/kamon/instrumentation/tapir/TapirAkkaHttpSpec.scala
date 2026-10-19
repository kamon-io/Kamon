/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
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

package kamon.instrumentation.tapir

import kamon.testkit.{InitAndStopKamonAfterAll, TestSpanReporter}
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, OptionValues}
import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend, UriContext, asStringAlways, basicRequest}

import scala.concurrent.duration.DurationInt

class TapirAkkaHttpSpec extends AnyWordSpec with Matchers
    with InitAndStopKamonAfterAll
    with TestSpanReporter
    with Eventually
    with OptionValues {

  var backend: SttpBackend[Identity, Any] = _
  var listenPort: Int = 0

  override def beforeAll() = {
    super.beforeAll()
    backend = HttpURLConnectionBackend()
    listenPort = TapirAkkaHttpServer.start()
  }

  override def afterAll() = {
    TapirAkkaHttpServer.stop()
    backend.close()
    super.afterAll()
  }

  "the Tapir Akka HTTP instrumentation" should {
    "replace params in path when naming span" in {
      basicRequest
        .response(asStringAlways)
        .get(uri"http://localhost:$listenPort/hello/frodo").send(backend)
        .body

      eventually(timeout(2.seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "/hello/{param1}"
      }
    }

    "replace params with mixed type params when naming span" in {
      basicRequest
        .response(asStringAlways)
        .get(uri"http://localhost:$listenPort/nested/path/with/3/mixed/true/types").send(backend)
        .body

      eventually(timeout(2.seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "/nested/{integer_param}/with/{param2}/mixed/{other_param}/types"
      }
    }
  }
}
