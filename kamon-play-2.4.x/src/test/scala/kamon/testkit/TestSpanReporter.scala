/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon.testkit

import java.util.concurrent.LinkedBlockingQueue

import com.typesafe.config.Config
import kamon.SpanReporter
import kamon.trace.Span
import kamon.trace.Span.FinishedSpan

class TestSpanReporter() extends SpanReporter {
  import scala.collection.JavaConverters._
  private val reportedSpans = new LinkedBlockingQueue[FinishedSpan]()

  override def reportSpans(spans: Seq[Span.FinishedSpan]): Unit =
    reportedSpans.addAll(spans.asJava)

  def nextSpan(): Option[FinishedSpan] =
    Option(reportedSpans.poll())

  def clear(): Unit =
    reportedSpans.clear()

  override def start(): Unit = {}
  override def stop(): Unit = {}
  override def reconfigure(config: Config): Unit = {}
}