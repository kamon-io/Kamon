/*
 * =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
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

package kamon.instrumentation.akka.instrumentations

import akka.actor.ActorRef
import akka.pattern.AskTimeoutException
import akka.util.Timeout
import kamon.Kamon
import kamon.instrumentation.akka.AkkaInstrumentation
import kamon.instrumentation.akka.AkkaInstrumentation.AskPatternTimeoutWarningSetting.{Heavyweight, Lightweight, Off}
import kamon.util.CallingThreadExecutionContext
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice.{Argument, OnMethodExit, Origin, Return}
import org.slf4j.LoggerFactory

import scala.annotation.static
import scala.concurrent.Future

class AskPatternInstrumentation extends InstrumentationBuilder {

  /**
    * Logs a warning message with various levels of detail when a Future[X] returned by the Ask pattern times out.
    */
  onType("akka.pattern.AskableActorRef$")
    .advise(method("$qmark$extension"), classOf[AskPatternInstrumentation])

}

object AskPatternInstrumentation {

  private val _log = LoggerFactory.getLogger(classOf[AskPatternInstrumentation])

  private class StackTraceCaptureException extends Throwable

  private case class SourceLocation (
    declaringType: String,
    method: String
  )

  @OnMethodExit(suppress = classOf[Throwable])
  @static def onExit(@Origin origin: String, @Return future: Future[AnyRef], @Argument(0) actor: ActorRef, @Argument(2) timeout: Timeout) = {

    if(AkkaPrivateAccess.isInternalAndActiveActorRef(actor) && Kamon.currentContext().nonEmpty()) {
      AkkaInstrumentation.settings().askPatternWarning match {
        case Off         =>
        case Lightweight => hookLightweightWarning(future, sourceLocation(origin), actor)
        case Heavyweight => hookHeavyweightWarning(future, new StackTraceCaptureException, actor)
      }
    }
  }

  private def ifAskTimeoutException(code: => Unit): PartialFunction[Throwable, Unit] = {
    case _: AskTimeoutException => code
    case _                      =>
  }


  private def hookLightweightWarning(future: Future[AnyRef], sourceLocation: SourceLocation, actor: ActorRef): Unit = {
    val locationString = Option(sourceLocation)
      .map(location => s"${location.declaringType}:${location.method}")
      .getOrElse("<unknown position>")

    future.failed.foreach(ifAskTimeoutException {
      _log.warn(s"Timeout triggered for ask pattern to actor [${actor.path.name}] at [$locationString]")
    })(CallingThreadExecutionContext)
  }

  private def hookHeavyweightWarning(future: Future[AnyRef], captureException: StackTraceCaptureException, actor: ActorRef): Unit = {
    val locationString = captureException.getStackTrace.drop(3).mkString("", System.lineSeparator, System.lineSeparator)

    future.failed.foreach(ifAskTimeoutException {
      _log.warn(s"Timeout triggered for ask pattern to actor [${actor.path.name}] at [$locationString]")
    })(CallingThreadExecutionContext)
  }

  private def sourceLocation(origin: String): SourceLocation = {
    val methodDescription = origin.split(" ")
    SourceLocation(methodDescription(0), methodDescription(1))
  }
}
