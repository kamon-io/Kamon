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
package spray.can.server

import org.aspectj.lang.annotation._
import kamon.trace.{ TraceRecorder, TraceContextAware }
import akka.actor.ActorSystem
import spray.http.HttpRequest
import akka.event.Logging.Warning
import scala.Some
import kamon.Kamon
import kamon.spray.Spray

@Aspect
class ServerRequestTracing {

  @DeclareMixin("spray.can.server.OpenRequestComponent.DefaultOpenRequest")
  def mixinContextAwareToOpenRequest: TraceContextAware = TraceContextAware.default

  @Pointcut("execution(spray.can.server.OpenRequestComponent$DefaultOpenRequest.new(..)) && this(openRequest) && args(*, request, *, *)")
  def openRequestInit(openRequest: TraceContextAware, request: HttpRequest): Unit = {}

  @After("openRequestInit(openRequest, request)")
  def afterInit(openRequest: TraceContextAware, request: HttpRequest): Unit = {
    val system: ActorSystem = openRequest.asInstanceOf[OpenRequest].context.actorContext.system
    val sprayExtension = Kamon(Spray)(system)

    val defaultTraceName: String = request.method.value + ": " + request.uri.path
    val token = if (sprayExtension.includeTraceToken) {
      request.headers.find(_.name == sprayExtension.traceTokenHeaderName).map(_.value)
    } else None

    TraceRecorder.start(defaultTraceName, token)(system)

    // Necessary to force initialization of traceContext when initiating the request.
    openRequest.traceContext
  }

  @Pointcut("execution(* spray.can.server.ServerFrontend$$anon$2$$anon$1.spray$can$server$ServerFrontend$$anon$$anon$$openNewRequest(..))")
  def openNewRequest(): Unit = {}

  @After("openNewRequest()")
  def afterOpenNewRequest(): Unit = {
    TraceRecorder.clearContext
  }

  @Pointcut("execution(* spray.can.server.OpenRequestComponent$DefaultOpenRequest.handleResponseEndAndReturnNextOpenRequest(..)) && target(openRequest)")
  def openRequestCreation(openRequest: TraceContextAware): Unit = {}

  @After("openRequestCreation(openRequest)")
  def afterFinishingRequest(openRequest: TraceContextAware): Unit = {
    val storedContext = openRequest.traceContext
    val incomingContext = TraceRecorder.currentContext
    TraceRecorder.finish()

    for (original ← storedContext) {
      incomingContext match {
        case Some(incoming) if original.token != incoming.token ⇒
          publishWarning(s"Different ids when trying to close a Trace, original: [$original] - incoming: [$incoming]")

        case Some(_) ⇒ // nothing to do here.

        case None ⇒
          publishWarning(s"Trace context not present while closing the Trace: [$original]")
      }
    }

    def publishWarning(text: String): Unit = {
      val system: ActorSystem = openRequest.asInstanceOf[OpenRequest].context.actorContext.system
      system.eventStream.publish(Warning("", classOf[ServerRequestTracing], text))
    }
  }
}
