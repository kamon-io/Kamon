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

package kamon.instrumentation.futures.scala

import com.typesafe.config.Config
import kamon.Kamon
import kamon.tag.TagSet
import kamon.trace.{Span, SpanBuilder}
import kamon.util.CallingThreadExecutionContext

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.util.control.NonFatal


object ScalaFutureInstrumentation {

  val Component = "scala.future"

  /**
    * Traces the execution of an asynchronous computation modeled by a Future. This function will create a Span when it
    * starts executing and set that Span as current while the code that created the actual Future executes and then,
    * automatically finish that Span when the created Future finishes execution.
    *
    * The purpose of this function is to make it easy to trace an asynchronous computation as a whole, even though such
    * computation might be the result of applying several transformations to an initial Future. If you are interested in
    * tracing the intermediate computations as well, take a look at the `traceBody` and `traceFunc` functions bellow.
    */
  def trace[T](operationName: String, tags: TagSet = TagSet.Empty, metricTags: TagSet = TagSet.Empty)(future: => Future[T])
      (implicit settings: Settings): Future[T] = {

    val spanBuilder = Kamon.internalSpanBuilder(operationName, Component)
    if(tags.nonEmpty()) spanBuilder.tag(tags)
    if(metricTags.nonEmpty()) spanBuilder.tagMetrics(metricTags)

    trace(spanBuilder)(future)(settings)
  }

  /**
    * Traces the execution of an asynchronous computation modeled by a Future. This function will create a Span when it
    * starts executing and set that Span as current while the code that created the actual Future executes and then,
    * automatically finish that Span when the created Future finishes execution.
    *
    * The purpose of this function is to make it easy to trace an asynchronous computation as a whole, even though such
    * computation might be the result of applying several transformations to an initial Future. If you are interested in
    * tracing the intermediate computations as well, take a look at the `traceBody` and `traceFunc` functions bellow.
    */
  def trace[T](spanBuilder: SpanBuilder)(future: => Future[T])(implicit settings: Settings): Future[T] = {
    if(settings.trackMetrics)
      spanBuilder.trackMetrics()
    else
      spanBuilder.doNotTrackMetrics()

    val futureSpan = spanBuilder.start()
    val evaluatedFuture = Kamon.runWithSpan(futureSpan, finishSpan = false)(future)
    evaluatedFuture.onComplete {
      case Success(_)       => futureSpan.finish()
      case Failure(failure) => futureSpan.fail(failure).finish()
    } (CallingThreadExecutionContext)

    evaluatedFuture
  }

  /**
    * Traces the execution of Future's body with a Delayed Span which takes into account the scheduling time when run
    * inside of a CallbackRunnable. This function is expected to be used when creating new Futures and wrapping the
    * entire body of the Future as follows:
    *
    *   Future(traceBody("myAsyncOperationName") {
    *     // Here goes the future body.
    *   })
    *
    * If the scheduling time cannot be determined this function will fall back to using a regular Span for tracking the
    * Future's body since the only reasonable value for the scheduling time would be the start time, which would
    * yield no wait time and the same processing and elapsed time, rendering the Delayed Span useless.
    *
    * IMPORTANT: Do not use this method if you are not going to run your application with instrumentation enabled. This
    *            method contains a call to Kamon.storeContext(...) which will only be cleaned up by the CallbackRunnable
    *            bytecode instrumentation. If instrumentation is disabled you risk leaving dirty threads that can cause
    *            incorrect Context propagation behavior.
    */
  def traceBody[S](operationName: String, tags: TagSet = TagSet.Empty, metricTags: TagSet = TagSet.Empty)(body: => S)
      (implicit settings: Settings): S = {

    val spanBuilder = Kamon.internalSpanBuilder(operationName, Component)
    if(tags.nonEmpty()) spanBuilder.tag(tags)
    if(metricTags.nonEmpty()) spanBuilder.tagMetrics(metricTags)

    traceBody(spanBuilder)(body)(settings)
  }

  /**
    * Traces the execution of Future's body with a Delayed Span which takes into account the scheduling time when run
    * inside of a CallbackRunnable. This function is expected to be used when creating new Futures and wrapping the
    * entire body of the Future as follows:
    *
    *   Future(traceBody("myAsyncOperationName") {
    *     // Here goes the future body.
    *   })
    *
    * If the scheduling time cannot be determined this function will fall back to using a regular Span for tracking the
    * Future's body since the only reasonable value for the scheduling time would be the start time, which would
    * yield no wait time and the same processing and elapsed time, rendering the Delayed Span useless.
    *
    * IMPORTANT: Do not use this method if you are not going to run your application with instrumentation enabled. This
    *            method contains a call to Kamon.storeContext(...) which will only be cleaned up by the CallbackRunnable
    *            bytecode instrumentation. If instrumentation is disabled you risk leaving dirty threads that can cause
    *            incorrect Context propagation behavior.
    */
  def traceBody[S](spanBuilder: SpanBuilder)(body: => S)(implicit settings: Settings): S = {
    val span = startedSpan(spanBuilder, settings)
    Kamon.storeContext(Kamon.currentContext().withEntry(Span.Key, span))

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
    * Traces the execution of a transformation on a Future with a Delayed Span which takes into account the scheduling time
    * when run inside of a CallbackRunnable. This function is expected to be used when creating callbacks on Futures and
    * should wrap the entire actual callback as follows:
    *
    *   Future(...)
    *     .map(traceFunc("myMapCallbackName")( // Here goes the actual callback. ))
    *     .filter(traceFunc("myFilterCallbackName")( // Here goes the actual callback. ))
    *
    * If the scheduling time cannot be determined this function will fall back to using a regular Span for tracking the
    * traced callback since the only reasonable value for the scheduling time would be the start time, which would yield
    * no wait time and the same processing and elapsed time, rendering the Delayed Span useless.
    *
    * IMPORTANT: Do not use this method if you are not going to run your application with instrumentation enabled. This
    *            method contains a call to Kamon.storeContext(...) which will only be cleaned up by the CallbackRunnable
    *            bytecode instrumentation. If instrumentation is disabled you risk leaving dirty threads that can cause
    *            incorrect Context propagation behavior.
    */
  def traceFunc[T, S](operationName: String, tags: TagSet = TagSet.Empty, metricTags: TagSet = TagSet.Empty)(body: T => S)
      (implicit settings: Settings): T => S = {

    val spanBuilder = Kamon.internalSpanBuilder(operationName, Component)
    if(tags.nonEmpty()) spanBuilder.tag(tags)
    if(metricTags.nonEmpty()) spanBuilder.tagMetrics(metricTags)

    traceFunc(spanBuilder)(body)(settings)
  }

  /**
    * Traces the execution of a transformation on a Future with a Delayed Span which takes into account the scheduling time
    * when run inside of a CallbackRunnable. This function is expected to be used when creating callbacks on Futures and
    * should wrap the entire actual callback as follows:
    *
    *   Future(...)
    *     .map(traceFunc("myMapCallbackName")( // Here goes the actual callback. ))
    *     .filter(traceFunc("myFilterCallbackName")( // Here goes the actual callback. ))
    *
    * If the scheduling time cannot be determined this function will fall back to using a regular Span for tracking the
    * traced callback since the only reasonable value for the scheduling time would be the start time, which would yield
    * no wait time and the same processing and elapsed time, rendering the Delayed Span useless.
    *
    * IMPORTANT: Do not use this method if you are not going to run your application with instrumentation enabled. This
    *            method contains a call to Kamon.storeContext(...) which will only be cleaned up by the CallbackRunnable
    *            bytecode instrumentation. If instrumentation is disabled you risk leaving dirty threads that can cause
    *            incorrect Context propagation behavior.
    */
  def traceFunc[T, S](spanBuilder: SpanBuilder)(body: T => S)(implicit settings: Settings): T => S = { t: T =>
    val span = startedSpan(spanBuilder, settings)
    Kamon.storeContext(Kamon.currentContext().withEntry(Span.Key, span))

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
    * Settings for the Delayed Spans created by the traceBody and traceFunc helper functions.
    */
  case class Settings(trackMetrics: Boolean, trackDelayedSpanMetrics: Boolean)

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
    val NoMetrics = Settings(false, false)

    /**
      * Disables tracking of Delayed Span metrics (i.e. the "span.wait-time" and "span.elapsed-time" metrics will not be
      * tracked).
      */
    val NoDelayedSpanMetrics = Settings(true, false)


    private def readSettingsFromConfig(config: Config): Settings = {
      val futureInstrumentationConfig = config.getConfig("kamon.instrumentation.futures.scala")

      Settings(
        trackMetrics = futureInstrumentationConfig.getBoolean("trace.track-span-metrics"),
        trackDelayedSpanMetrics = futureInstrumentationConfig.getBoolean("trace.track-span-metrics")
      )
    }
  }

  /**
    * Starts a regular Span or a Delayed Span, depending on whether the scheduling time of the CallbackRunnable where
    * we are executing at the moment (if any) can be determined.
    */
  private def startedSpan(spanBuilder: SpanBuilder, settings: Settings): Span = {
    CallbackRunnableRunInstrumentation.currentRunnableScheduleTimestamp() match {
      case Some(timestamp) =>
        val scheduledAt = Kamon.clock().toInstant(timestamp)
        val span = spanBuilder.delay(scheduledAt)
        if(!settings.trackMetrics) span.doNotTrackMetrics()
        if(!settings.trackDelayedSpanMetrics) span.doNotTrackDelayedSpanMetrics()
        span.start()

      case None =>
        if(!settings.trackMetrics) spanBuilder.doNotTrackMetrics()
        spanBuilder.start()
    }
  }
}

