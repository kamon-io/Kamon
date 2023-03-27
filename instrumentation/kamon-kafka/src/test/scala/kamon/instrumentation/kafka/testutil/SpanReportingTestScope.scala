/* =========================================================================================
 * Copyright © 2013-2019 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License") you may not use this file
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
package kamon.instrumentation.kafka.testutil

import kamon.testkit.TestSpanReporter
import kamon.trace.Span
import org.scalatest.concurrent.{Eventually, PatienceConfiguration}
import org.scalatest.matchers.should.Matchers

abstract class SpanReportingTestScope(_reporter: TestSpanReporter.BufferingSpanReporter) extends Eventually with Matchers {
  private var _reportedSpans: List[Span.Finished] = Nil
  _reporter.clear()


  def reportedSpans: List[Span.Finished] = _reportedSpans

  def assertNoSpansReported(): Unit =
    _reporter.nextSpan() shouldBe None

  def awaitReportedSpans(waitBetweenPollInMs: Int = 100): Unit = {
    def doIt(prevNumReportedSpans: Int): Unit = {
      Thread.sleep(waitBetweenPollInMs)
      collectReportedSpans()
      if(reportedSpans.size != prevNumReportedSpans)
        doIt(reportedSpans.size)
    }
    doIt(reportedSpans.size)
  }

  def awaitNumReportedSpans(numOfExpectedSpans: Int)(implicit timeout: PatienceConfiguration.Timeout): Unit = {
    eventually(timeout) {
      collectReportedSpans()
      _reportedSpans.size shouldBe numOfExpectedSpans
    }
    Thread.sleep(300)
    if(_reporter.nextSpan().isDefined) {
      fail(s"Expected only $numOfExpectedSpans spans to be reported, but got more!")
    }
  }

  def collectReportedSpans(): Unit =
    _reporter.nextSpan().foreach { s =>
      _reportedSpans = s :: _reportedSpans
    }

  def assertNumSpansForOperation(operationName: String, numExpectedSpans: Int): Unit =
    reportedSpans.filter(_.operationName == operationName) should have size numExpectedSpans

  def assertReportedSpan[T](p: Span.Finished => Boolean)(f: Span.Finished => T): T = {
    _reportedSpans.filter(p) match {
      case Nil =>
        fail("expected span not found!")
      case x :: Nil =>
        f(x)
      case x :: y =>
        fail(s"More than one span found! spans:${x :: y}")
    }
  }

  def assertReportedSpans(p: Span.Finished => Boolean)(f: List[Span.Finished] => Unit): Unit = {
    _reportedSpans.filter(p) match {
      case Nil =>
        fail("expected span not found!")
      case x =>
        f(x)
    }
  }
}
