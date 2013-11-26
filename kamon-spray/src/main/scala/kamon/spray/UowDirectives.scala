/* ===================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */
package kamon.spray

import spray.routing.directives.BasicDirectives
import spray.routing._
import java.util.concurrent.atomic.AtomicLong
import scala.util.Try
import java.net.InetAddress
import kamon.trace.Trace

trait UowDirectives extends BasicDirectives {
  def uow: Directive0 = mapRequest { request ⇒
    val uowHeader = request.headers.find(_.name == "X-UOW")

    val generatedUow = uowHeader.map(_.value).getOrElse(UowDirectives.newUow)
    Trace.transformContext(_.copy(uow = generatedUow))
    request
  }
}

object UowDirectives {
  val uowCounter = new AtomicLong
  val hostnamePrefix = Try(InetAddress.getLocalHost.getHostName).getOrElse("unknown-localhost")
  def newUow = "%s-%s".format(hostnamePrefix, uowCounter.incrementAndGet())

}