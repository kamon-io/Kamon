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

package kamon.testkit

import java.util.concurrent.LinkedBlockingQueue

import scala.collection.JavaConverters._
import com.typesafe.config.Config
import kamon.module.SpanReporter
import kamon.{Kamon, testkit}
import kamon.trace.Span

import scala.concurrent.duration.Duration

/**
  * A Mixin that creates and initializes an inspectable Span reporter, setting up the typical options required for it
  * to work as expected (sample always and fast span flushing).
  */
trait TestSpanReporter extends Reconfigure { self =>
  private val _reporter = new testkit.TestSpanReporter.BufferingSpanReporter()
  private val _registration = {
    sampleAlways()
    enableFastSpanFlushing()
    Kamon.registerModule("test-span-reporter-" + self.getClass.getSimpleName, _reporter)
  }

  /**
    * Returns the test reporter instance.
    */
  def testSpanReporter(): TestSpanReporter.BufferingSpanReporter =
    _reporter

  /**
    * Shuts down the test reporter. Once it has been shut down it will no longer receive newly reported Spans, but it
    * can still be inspected.
    */
  def shutdownTestSpanReporter(): Unit =
    _registration.cancel()
}

object TestSpanReporter {

  /**
    * A SpanReporter that buffers all reported Spans and exposes them.
    */
  class BufferingSpanReporter extends SpanReporter {
    private val _reportedSpans = new LinkedBlockingQueue[Span.Finished]()

    /**
      * Returns and discards the latest received Span, if any.
      */
    def nextSpan(): Option[Span.Finished] =
      Option(_reportedSpans.poll())

    /**
      * Discards all received Spans.
      */
    def clear(): Unit =
      _reportedSpans.clear()

    /**
      * Returns all remaining Spans in the buffer.
      */
    def spans(): Seq[Span.Finished] =
      _reportedSpans.toArray(Array.ofDim[Span.Finished](0)).toSeq

    /**
      * Returns all remaining Spans in the buffer after waiting for the provided delay.
      */
    def spans(delay: Duration): Seq[Span.Finished] = {
      Thread.sleep(delay.toMillis)
      spans()
    }

    /**
      * Returns all remaining Spans in the buffer after waiting for the provided delay.
      */
    def spans(delay: java.time.Duration): Seq[Span.Finished] = {
      Thread.sleep(delay.toMillis)
      spans()
    }

    // Here go the reporter-specific implementation details:
    override def stop(): Unit = {}
    override def reconfigure(config: Config): Unit = {}
    override def reportSpans(spans: Seq[Span.Finished]): Unit =
      _reportedSpans.addAll(spans.asJava)
  }
}
