/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon.annotation.util

import kamon.context.Context
import kamon.metric.{RangeSampler, Timer}
import kamon.trace.{Span, Tracer}
import kamon.util.CallingThreadExecutionContext

import scala.concurrent.Future
import scala.util.{Failure, Success}

object Hooks {

  def key(): Context.Key[Tracer.PreStartHook] =
    kamon.trace.Hooks.PreStart.Key

  def updateOperationName(operationName: String): Tracer.PreStartHook =
    kamon.trace.Hooks.PreStart.updateOperationName(operationName)

  def finishSpanOnComplete[T](future: Future[T], span: Span): Unit =
    future.onComplete {
      case Success(_) => span.finish()
      case Failure(t) => span.fail(t).finish()
    }(CallingThreadExecutionContext)

  def decrementRangeSamplerOnComplete[T](future: Future[T], rangeSampler: RangeSampler): Unit =
    future.onComplete { _ => rangeSampler.decrement() }(CallingThreadExecutionContext)

  def stopTimerOnComplete[T](future: Future[T], timer: Timer.Started): Unit =
    future.onComplete { _ => timer.stop() }(CallingThreadExecutionContext)

}
