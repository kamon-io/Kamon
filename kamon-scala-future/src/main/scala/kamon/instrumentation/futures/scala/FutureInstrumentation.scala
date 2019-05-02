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

package kamon.instrumentation.futures.scala

import com.typesafe.config.Config
import kamon.Kamon
import kamon.trace.{Span, SpanBuilder}

import scala.util.control.NonFatal


object FutureInstrumentation {

  val Component = "scala.future"

  /**
    * Traces the execution of a block of code with a Delayed Span which takes into account the scheduling time when run
    * inside of a CallbackRunnable. This function is expected to be used when creating new Futures and wrapping the
    * entire body of the Future as follows:
    *
    *   Future(traced("myAsyncOperationName") {
    *     // Here goes the future body.
    *   })
    *
    * If the scheduling time cannot be determined this function will fall back to using a regular Span for tracking the
    * traced operation since the only reasonable value for the scheduling time would be the start time, which would
    * yield no wait time and the same processing and elapsed time, rendering the Delayed Span useless.
    *
    * IMPORTANT: Do not use this method if you are not going to run your application with instrumentation enabled. This
    *            method contains a call to Kamon.store(...) which will only be cleaned up by the CallbackRunnable
    *            bytecode instrumentation. If instrumentation is disabled you risk leaving dirty threads that can cause
    *            incorrect Context propagation behavior.
    */
  def traced[S](operationName: String)(body: => S)(implicit settings: Settings): S = {
    val span = startedSpan(operationName, settings)
    Kamon.store(Kamon.currentContext().withKey(Span.Key, span))

    try {
      body
    } catch {
      case NonFatal(error) =>
        span.fail(error.getMessage, error)
        throw error
    } finally {
      span.finish()
    }
  }

  /**
    * Traces the execution of a callback on a Future with a Delayed Span which takes into account the scheduling time
    * when run inside of a CallbackRunnable. This function is expected to be used when creating callbacks on Futures and
    * should wrap the entire actual callback as follows:
    *
    *   Future(...)
    *     .map(tracedCallback("myMapCallbackName")( // Here goes the actual callback. ))
    *     .filter(tracedCallback("myFilterCallbackName")( // Here goes the actual callback. ))
    *
    * If the scheduling time cannot be determined this function will fall back to using a regular Span for tracking the
    * traced callback since the only reasonable value for the scheduling time would be the start time, which would yield
    * no wait time and the same processing and elapsed time, rendering the Delayed Span useless.
    *
    * IMPORTANT: Do not use this method if you are not going to run your application with instrumentation enabled. This
    *            method contains a call to Kamon.store(...) which will only be cleaned up by the CallbackRunnable
    *            bytecode instrumentation. If instrumentation is disabled you risk leaving dirty threads that can cause
    *            incorrect Context propagation behavior.
    */
  def tracedCallback[T, S](operationName: String)(body: T => S)(implicit settings: Settings): T => S = { t: T =>
    val span = startedSpan(operationName, settings)
    Kamon.store(Kamon.currentContext().withKey(Span.Key, span))

    try {
      body(t)
    } catch {
      case NonFatal(error) =>
        span.fail(error.getMessage, error)
        throw error
    } finally {
      span.finish()
    }
  }


  /**
    * Settings for the Delayed Spans created by the traced and tracedCallback helper functions.
    */
  case class Settings(trackMetrics: Boolean, trackDelayedSpanMetrics: Boolean, builderTransformation: SpanBuilder => SpanBuilder) {
    def transform(t: SpanBuilder => SpanBuilder): Settings =
      copy(builderTransformation = t)
  }

  object Settings {

    @volatile private var _settingsFromConfig = readSettingsFromConfig(Kamon.config())
    Kamon.onReconfigure(config => _settingsFromConfig = readSettingsFromConfig(config))

    /**
      * Returns the settings from the current Kamon configuration.
      */
    implicit def fromConfig: Settings =
      _settingsFromConfig

    /**
      * Completely disables tracking metrics on traced blocks and callbacks.
      */
    val NoMetrics = Settings(false, false, identity)

    /**
      * Disables tracking of Delayed Span metrics (i.e. the "span.wait-time" and "span.elapsed-time" metrics will not be
      * tracked).
      */
    val NoDelayedSpanMetrics = Settings(true, false, identity)


    private def readSettingsFromConfig(config: Config): Settings = {
      val futureInstrumentationConfig = config.getConfig("kamon.instrumentation.futures.scala")

      Settings(
        trackMetrics = futureInstrumentationConfig.getBoolean("trace.track-span-metrics"),
        trackDelayedSpanMetrics = futureInstrumentationConfig.getBoolean("trace.track-span-metrics"),
        builderTransformation = identity
      )
    }
  }

  /**
    * Starts a regular Span or a Delayed Span, depending on whether the scheduling time of the CallbackRunnable where
    * we are executing at the moment (if any) can be determined.
    */
  private def startedSpan(operationName: String, settings: Settings): Span = {
    CallbackRunnableRunInstrumentation.currentRunnableScheduleTimestamp() match {
      case Some(timestamp) =>
        val scheduledAt = Kamon.clock().toInstant(timestamp)
        val span = settings.builderTransformation(Kamon.internalSpanBuilder(operationName, Component)).delay(scheduledAt)
        if(!settings.trackMetrics) span.doNotTrackMetrics()
        if(!settings.trackDelayedSpanMetrics) span.doNotTrackDelayedSpanMetrics()
        span.start()

      case None =>
        val span = settings.builderTransformation(Kamon.internalSpanBuilder(operationName, Component))
        if(!settings.trackMetrics) span.doNotTrackMetrics()
        span.start()
    }
  }
}

