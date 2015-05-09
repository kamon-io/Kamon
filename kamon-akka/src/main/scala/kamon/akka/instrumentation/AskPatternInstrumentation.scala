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
import kamon.Kamon
import kamon.akka.{ AkkaExtension, Akka }
import kamon.akka.AskPatternTimeoutWarningSettings.{ Heavyweight, Lightweight, Off }
import akka.actor.{ InternalActorRef, ActorRef }
import akka.pattern.AskTimeoutException
import kamon.trace.Tracer
import kamon.util.SameThreadExecutionContext
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._
import org.aspectj.lang.reflect.SourceLocation
import scala.concurrent.Future
import scala.compat.Platform.EOL

@Aspect
class AskPatternInstrumentation {

  import AskPatternInstrumentation._

  @Pointcut("call(* akka.pattern.AskableActorRef$.$qmark$extension(..)) && args(actor, *, timeout)")
  def askableActorRefAsk(actor: ActorRef, timeout: Timeout): Unit = {}

  @Around("askableActorRefAsk(actor, timeout)")
  def hookAskTimeoutWarning(pjp: ProceedingJoinPoint, actor: ActorRef, timeout: Timeout): AnyRef =
    actor match {
      // the AskPattern will only work for InternalActorRef's with these conditions.
      case ref: InternalActorRef if !ref.isTerminated && timeout.duration.length > 0 && Tracer.currentContext.nonEmpty ⇒
        val akkaExtension = Kamon.extension(Akka)
        val future = pjp.proceed().asInstanceOf[Future[AnyRef]]

        akkaExtension.askPatternTimeoutWarning match {
          case Off         ⇒
          case Lightweight ⇒ hookLightweightWarning(future, pjp.getSourceLocation, actor, akkaExtension)
          case Heavyweight ⇒ hookHeavyweightWarning(future, new StackTraceCaptureException, actor, akkaExtension)
        }

        future

      case _ ⇒ pjp.proceed().asInstanceOf[Future[AnyRef]] //
    }

  def ifAskTimeoutException(code: ⇒ Unit): PartialFunction[Throwable, Unit] = {
    case tmo: AskTimeoutException ⇒ code
    case _                        ⇒
  }

  def hookLightweightWarning(future: Future[AnyRef], sourceLocation: SourceLocation, actor: ActorRef, akkaExtension: AkkaExtension): Unit = {
    val locationString = Option(sourceLocation)
      .map(location ⇒ s"${location.getFileName}:${location.getLine}")
      .getOrElse("<unknown position>")

    future.onFailure(ifAskTimeoutException {
      akkaExtension.log.warning("Timeout triggered for ask pattern to actor [{}] at [{}]", actor.path.name, locationString)
    })(SameThreadExecutionContext)
  }

  def hookHeavyweightWarning(future: Future[AnyRef], captureException: StackTraceCaptureException, actor: ActorRef, akkaExtension: AkkaExtension): Unit = {
    val locationString = captureException.getStackTrace.drop(3).mkString("", EOL, EOL)

    future.onFailure(ifAskTimeoutException {
      akkaExtension.log.warning("Timeout triggered for ask pattern to actor [{}] at [{}]", actor.path.name, locationString)
    })(SameThreadExecutionContext)
  }
}

object AskPatternInstrumentation {
  class StackTraceCaptureException extends Throwable
}