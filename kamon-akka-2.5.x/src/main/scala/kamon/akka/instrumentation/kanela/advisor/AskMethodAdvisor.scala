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

package akka.kamon.instrumentation.kanela.advisor

import akka.actor.{ActorRef, InternalActorRef}
import akka.kamon.instrumentation.AskPatternInstrumentation
import akka.pattern.AskTimeoutException
import akka.util.Timeout
import kamon.Kamon
import kamon.akka.Akka
import kamon.akka.AskPatternTimeoutWarningSettings.{Heavyweight, Lightweight, Off}
import kamon.context.Context
import kamon.util.CallingThreadExecutionContext
import kanela.agent.libs.net.bytebuddy.asm.Advice.{Argument, OnMethodExit, Origin, Return}
import org.slf4j.LoggerFactory

import scala.compat.Platform.EOL
import scala.concurrent.Future

/**
  * Advisor for akka.pattern.AskableActorRef::$qmark$extension
  */
class AskMethodAdvisor
object AskMethodAdvisor {

  private val logger = LoggerFactory.getLogger(classOf[AskPatternInstrumentation])

  class StackTraceCaptureException extends Throwable

  case class SourceLocation(declaringType: String, method: String)
  object SourceLocation {
    def apply(origin: String) = {
      val methodDescription = origin.split(" ")
      new SourceLocation(methodDescription(0), methodDescription(1))
    }
  }

  @OnMethodExit(suppress = classOf[Throwable])
  def onExit(@Origin origin: String,
             @Return future: Future[AnyRef],
             @Argument(0) actor: ActorRef,
             @Argument(2) timeout: Timeout) = {

    // the AskPattern will only work for InternalActorRef's with these conditions.
    actor match {
      case internalActorRef: InternalActorRef ⇒
        if (!internalActorRef.isTerminated && timeout.duration.length > 0 && Kamon.currentContext() != Context.Empty) {
          Akka.askPatternTimeoutWarning match {
            case Off         ⇒
            case Lightweight ⇒ hookLightweightWarning(future, SourceLocation(origin), actor)
            case Heavyweight ⇒ hookHeavyweightWarning(future, new StackTraceCaptureException, actor)
          }
          hookHeavyweightWarning(future, new StackTraceCaptureException, actor)
        }
      case _ ⇒
    }
  }

  def ifAskTimeoutException(code: ⇒ Unit): PartialFunction[Throwable, Unit] = {
    case tmo: AskTimeoutException ⇒ code
    case _                        ⇒
  }

  def hookLightweightWarning(future: Future[AnyRef], sourceLocation: SourceLocation, actor: ActorRef): Unit = {
    val locationString = Option(sourceLocation)
      .map(location ⇒ s"${location.declaringType}:${location.method}")
      .getOrElse("<unknown position>")

    future.failed.foreach(ifAskTimeoutException {
      logger.warn(s"Timeout triggered for ask pattern to actor [${actor.path.name}] at [$locationString]")
    })(CallingThreadExecutionContext)
  }

  def hookHeavyweightWarning(future: Future[AnyRef], captureException: StackTraceCaptureException, actor: ActorRef): Unit = {
    val locationString = captureException.getStackTrace.drop(3).mkString("", EOL, EOL)

    future.failed.foreach(ifAskTimeoutException {
      logger.warn(s"Timeout triggered for ask pattern to actor [${actor.path.name}] at [$locationString]")
    })(CallingThreadExecutionContext)
  }
}


