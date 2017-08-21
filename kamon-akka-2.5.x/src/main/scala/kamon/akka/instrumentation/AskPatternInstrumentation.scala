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

import akka.util.Timeout
import kamon.akka.Akka
import kamon.akka.AskPatternTimeoutWarningSettings.{Heavyweight, Lightweight, Off}
import akka.actor.{ActorRef, InternalActorRef}
import akka.pattern.AskTimeoutException
import kamon.Kamon
import kamon.context.Context
import kamon.util.CallingThreadExecutionContext
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._
import org.aspectj.lang.reflect.SourceLocation
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.compat.Platform.EOL

@Aspect
class AskPatternInstrumentation {
  private val logger = LoggerFactory.getLogger(classOf[AskPatternInstrumentation])

  import AskPatternInstrumentation._

  @Pointcut("call(* akka.pattern.AskableActorRef$.$qmark$extension(..)) && args(actor, *, timeout)")
  def askableActorRefAsk(actor: ActorRef, timeout: Timeout): Unit = {}

  @Around("askableActorRefAsk(actor, timeout)")
  def hookAskTimeoutWarning(pjp: ProceedingJoinPoint, actor: ActorRef, timeout: Timeout): AnyRef =
    actor match {
      // the AskPattern will only work for InternalActorRef's with these conditions.
      case ref: InternalActorRef if !ref.isTerminated && timeout.duration.length > 0 && Kamon.currentContext() != Context.Empty ⇒
        val future = pjp.proceed().asInstanceOf[Future[AnyRef]]

        Akka.askPatternTimeoutWarning match {
          case Off         ⇒
          case Lightweight ⇒ hookLightweightWarning(future, pjp.getSourceLocation, actor)
          case Heavyweight ⇒ hookHeavyweightWarning(future, new StackTraceCaptureException, actor)
        }

        future

      case _ ⇒
        pjp.proceed().asInstanceOf[Future[AnyRef]]

    }

  def ifAskTimeoutException(code: ⇒ Unit): PartialFunction[Throwable, Unit] = {
    case tmo: AskTimeoutException ⇒ code
    case _                        ⇒
  }

  def hookLightweightWarning(future: Future[AnyRef], sourceLocation: SourceLocation, actor: ActorRef): Unit = {
    val locationString = Option(sourceLocation)
      .map(location ⇒ s"${location.getFileName}:${location.getLine}")
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

object AskPatternInstrumentation {
  class StackTraceCaptureException extends Throwable
}