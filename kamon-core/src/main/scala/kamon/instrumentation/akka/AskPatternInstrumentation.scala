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
import kamon.extension.akka.Akka
import kamon.trace.{ TraceContext, EmptyTraceContext, TraceContextAware }
import akka.actor.{ ActorSystem, ActorRef }
import akka.event.Logging.Warning
import akka.pattern.AskTimeoutException
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._
import org.aspectj.lang.reflect.SourceLocation
import scala.concurrent.Future
import scala.compat.Platform.EOL

@Aspect
class AskPatternInstrumentation {

  import AskPatternInstrumentation._

  @DeclareMixin("akka.pattern.AskableActorRef$")
  def mixinContextAwareToAskableActorRef: TraceContextAware = TraceContextAware.default

  @Pointcut("call(* akka.pattern.AskableActorRef$.$qmark$extension(..)) && target(ctx) && args(actor, *, *)")
  def askableActorRefAsk(ctx: TraceContextAware, actor: ActorRef): Unit = {}

  @Around("askableActorRefAsk(ctx, actor)")
  def hookAskTimeoutWarning(pjp: ProceedingJoinPoint, ctx: TraceContextAware, actor: ActorRef): AnyRef = ctx.traceContext match {
    case EmptyTraceContext ⇒ pjp.proceed()
    case ctx: TraceContext ⇒
      implicit val system = ctx.system
      val akkaExtension = Kamon(Akka)(system)

      val future = pjp.proceed().asInstanceOf[Future[AnyRef]]

      val handler = akkaExtension.askPatternTimeoutWarning match {
        case "off"         ⇒ None
        case "lightweight" ⇒ Some(errorHandler(callInfo = Some(CallInfo(s"${actor.path.name} ?", pjp.getSourceLocation))))
        case "heavyweight" ⇒ Some(errorHandler(stack = Some(new StackTraceCaptureException)))
      }

      handler.map(future.onFailure(_)(akkaExtension.dispatcher))
      future
  }

  def errorHandler(callInfo: Option[CallInfo] = None, stack: Option[StackTraceCaptureException] = None)(implicit system: ActorSystem): ErrorHandler = {
    case e: AskTimeoutException ⇒
      val message = {
        if (stack.isDefined) stack.map(s ⇒ s.getStackTrace.drop(3).mkString("", EOL, EOL))
        else callInfo.map(_.message)
      }
      publish(message)
  }

  def publish(message: Option[String])(implicit system: ActorSystem) = message map { msg ⇒
    system.eventStream.publish(Warning("AskPatternTracing", classOf[AskPatternInstrumentation],
      s"Timeout triggered for ask pattern registered at: $msg"))
  }
}

object AskPatternInstrumentation {
  type ErrorHandler = PartialFunction[Throwable, Unit]

  class StackTraceCaptureException extends Throwable

  case class CallInfo(name: String, sourceLocation: SourceLocation) {
    def message: String = {
      def locationInfo: String = Option(sourceLocation).map(location ⇒ s"${location.getFileName}:${location.getLine}").getOrElse("<unknown position>")
      def line: String = s"$name @ $locationInfo"
      s"$line"
    }
  }
}