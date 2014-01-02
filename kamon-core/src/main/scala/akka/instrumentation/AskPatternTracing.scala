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
package akka.instrumentation

import org.aspectj.lang.annotation.{ AfterReturning, Pointcut, Aspect }
import akka.event.Logging.Warning
import scala.compat.Platform.EOL
import akka.actor.ActorRefProvider
import akka.pattern.{ AskTimeoutException, PromiseActorRef }

@Aspect
class AskPatternTracing {

  class StackTraceCaptureException extends Throwable

  @Pointcut(value = "execution(* akka.pattern.PromiseActorRef$.apply(..)) && args(provider, *)", argNames = "provider")
  def promiseActorRefApply(provider: ActorRefProvider): Unit = {
    provider.settings.config.getBoolean("kamon.trace.ask-pattern-tracing")
  }

  @AfterReturning(pointcut = "promiseActorRefApply(provider)", returning = "promiseActor")
  def hookAskTimeoutWarning(provider: ActorRefProvider, promiseActor: PromiseActorRef): Unit = {
    val future = promiseActor.result.future
    val system = promiseActor.provider.guardian.underlying.system
    implicit val ec = system.dispatcher
    val stack = new StackTraceCaptureException

    future onFailure {
      case timeout: AskTimeoutException ⇒
        val stackString = stack.getStackTrace.drop(3).mkString("", EOL, EOL)

        system.eventStream.publish(Warning("AskPatternTracing", classOf[AskPatternTracing],
          "Timeout triggered for ask pattern registered at: " + stackString))
    }
  }
}
