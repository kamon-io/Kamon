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

import org.aspectj.lang.annotation.{ After, Pointcut, DeclareMixin, Aspect }
import kamon.trace.{ Trace, ContextAware }
import spray.http.HttpRequest
import akka.actor.ActorSystem
import akka.event.Logging.Warning

@Aspect
class ServerRequestTracing {

  @DeclareMixin("spray.can.server.OpenRequestComponent.DefaultOpenRequest")
  def mixinContextAwareToOpenRequest: ContextAware = ContextAware.default

  @Pointcut("execution(spray.can.server.OpenRequestComponent$DefaultOpenRequest.new(..)) && this(openRequest) && args(*, request, *, *)")
  def openRequestInit(openRequest: ContextAware, request: HttpRequest): Unit = {}

  @After("openRequestInit(openRequest, request)")
  def afterInit(openRequest: ContextAware, request: HttpRequest): Unit = {
    val system: ActorSystem = openRequest.asInstanceOf[OpenRequest].context.actorContext.system
    val defaultTraceName: String = request.method.value + ": " + request.uri.path

    Trace.start(defaultTraceName)(system)

    // Necessary to force initialization of traceContext when initiating the request.
    openRequest.traceContext
  }

  @Pointcut("execution(* spray.can.server.OpenRequestComponent$DefaultOpenRequest.handleResponseEndAndReturnNextOpenRequest(..)) && target(openRequest)")
  def openRequestCreation(openRequest: ContextAware): Unit = {}

  @After("openRequestCreation(openRequest)")
  def afterFinishingRequest(openRequest: ContextAware): Unit = {
    val storedContext = openRequest.traceContext
    val incomingContext = Trace.finish()

    for (original ← storedContext) {
      incomingContext match {
        case Some(incoming) if original.id != incoming.id ⇒
          publishWarning(s"Different ids when trying to close a Trace, original: [$original] - incoming: [$incoming]")

        case Some(_) ⇒ // nothing to do here.

        case None ⇒
          original.finish
          publishWarning(s"Trace context not present while closing the Trace: [$original]")
      }
    }

    def publishWarning(text: String): Unit = {
      val system: ActorSystem = openRequest.asInstanceOf[OpenRequest].context.actorContext.system
      system.eventStream.publish(Warning("", classOf[ServerRequestTracing], text))
    }
  }
}
