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

package akka.kamon.instrumentation

import kamon.Kamon
import kamon.trace.{ Trace, TraceContextAware }
import akka.actor.ActorRef
import akka.event.Logging.Warning
import akka.pattern.AskTimeoutException
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._
import org.aspectj.lang.reflect.SourceLocation
import scala.concurrent.Future

@Aspect
class AskPatternInstrumentation {

  @DeclareMixin("akka.pattern.AskableActorRef$")
  def mixinContextAwareToAskableActorRef: TraceContextAware = TraceContextAware.default

  @Pointcut("call(* akka.pattern.AskableActorRef$.$qmark$extension(..)) && target(traceContext) && args(actor, *, *)")
  def askableActorRefAsk(traceContext: TraceContextAware, actor: ActorRef): Unit = {}

  @Around("askableActorRefAsk(traceContext,actor)")
  def hookAskTimeoutWarning(pjp: ProceedingJoinPoint, traceContext: TraceContextAware, actor: ActorRef): AnyRef = {
    val callInfo = CallInfo(s"${actor.path.name} ?", pjp.getSourceLocation)
    val system = traceContext.traceContext.system
    val traceExtension = Kamon(Trace)(system)

    val future = pjp.proceed().asInstanceOf[Future[AnyRef]]

    if (traceExtension.enableAskPatternTracing) {
      future.onFailure {
        case timeout: AskTimeoutException ⇒
          system.eventStream.publish(Warning("AskPatternTracing", classOf[AskPatternInstrumentation],
            s"Timeout triggered for ask pattern registered at: ${callInfo.getMessage}"))
      }(traceExtension.defaultDispatcher)
    }
    future
  }

  case class CallInfo(name: String, sourceLocation: SourceLocation) {
    def getMessage: String = {
      def locationInfo: String = Option(sourceLocation).map(location ⇒ s"${location.getFileName}:${location.getLine}").getOrElse("<unknown position>")
      def line: String = s"$name @ $locationInfo"
      s"$line"
    }
  }
}
//"""Timeout triggered for actor [actor-que-pregunta] asking [actor-al-que-le-pregunta] @ [source location]"?"""