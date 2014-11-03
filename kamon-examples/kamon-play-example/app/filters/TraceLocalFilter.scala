/* ===================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

package filters

import kamon.trace.{TraceRecorder, TraceLocal}
import play.api.Logger
import play.api.mvc.{Result, RequestHeader, Filter}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future

case class TraceLocalContainer(traceToken:String, importantHeader:String)

object TraceLocalKey extends TraceLocal.TraceLocalKey {
  type ValueType = TraceLocalContainer
}

/*
 By default kamon spreads the trace-token-header-name, but sometimes is necessary pass through the application requests with some information like
 extra headers, with kamon it's possible using the TraceLocalStorage, in Play applications we can do an Action Filter or using Action Composition,
 in this example we are using a simple filter where given a Header store the value and then put the value in the result headers..

 More detailed usage of TraceLocalStorage: https://github.com/kamon-io/Kamon/blob/b17539d231da923ea854c01d2c69eb02ef1e85b1/kamon-core/src/test/scala/kamon/trace/TraceLocalSpec.scala
 */
object TraceLocalFilter extends Filter {
  val logger = Logger(this.getClass)
  val TraceLocalStorageKey = "MyTraceLocalStorageKey"

  override def apply(next: (RequestHeader) ⇒ Future[Result])(header: RequestHeader): Future[Result] = {

    def onResult(result:Result) = {
        val traceLocalContainer = TraceLocal.retrieve(TraceLocalKey).getOrElse(TraceLocalContainer("unknown","unknown"))
        logger.info(s"traceTokenValue: ${traceLocalContainer.traceToken}")
        result.withHeaders((TraceLocalStorageKey -> traceLocalContainer.traceToken))
    }

    //update the TraceLocalStorage
    TraceLocal.store(TraceLocalKey)(TraceLocalContainer(header.headers.get(TraceLocalStorageKey).getOrElse("unknown"), "unknown"))

    //call the action
    next(header).map(onResult)
  }
}
