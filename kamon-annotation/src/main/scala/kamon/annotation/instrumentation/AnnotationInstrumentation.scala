/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

package kamon.annotation.instrumentation

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.annotation.Counted
import kamon.annotation.CounterType.{MinMaxCounter, Counter}
import kamon.annotation._
import kamon.metric.{ GaugeKey, HistogramKey, UserMetrics }
import kamon.trace.{ TraceContext, Tracer }
import kamon.util.Latency
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.{ After, AfterReturning, Around, Aspect }

@Aspect
class AnnotationInstrumentation {
  implicit lazy val system = AnnotationBla.system

  @Around("execution(@kamon.annotation.Trace * *(..)) && @annotation(trace)")
  def trace(pjp: ProceedingJoinPoint, trace: Trace): AnyRef = {
    TraceContext.withContext(Kamon(Tracer).newContext(trace.value())) {
      val result = pjp.proceed()
      TraceContext.currentContext.finish()
      result
    }
  }

  @Around("execution(@kamon.annotation.Segment * *(..)) && @annotation(segment)")
  def segment(pjp: ProceedingJoinPoint, segment: Segment): AnyRef = {
    TraceContext.map { ctx ⇒
      val current = ctx.startSegment(segment.name(), segment.category, segment.library)
      val result = pjp.proceed()
      current.finish()
      result
    }
  }

  @Around("execution(@kamon.annotation.Timed * *(..)) && @annotation(timed) && this(obj)")
  def timed(pjp: ProceedingJoinPoint, timed: Timed, obj: AnyRef): AnyRef = {
    val histogram = Kamon(UserMetrics).histogram(HistogramKey(timed.name()))
    Latency.measure(histogram)(pjp.proceed)
  }

  @After("execution(@kamon.annotation.Counted * *(..)) && @annotation(counted) && this(obj)")
  def count(counted: Counted, obj: AnyRef): Unit = counted.`type`() match {
    case Counter       ⇒ Kamon(UserMetrics).counter(counted.name()).increment()
    case MinMaxCounter ⇒ Kamon(UserMetrics).minMaxCounter(counted.name()).increment()
  }

  @AfterReturning(pointcut = "execution(@kamon.annotation.Gauge * *(..)) && @annotation(gauge) && this(obj)", returning = "result")
  def gauge(gauge: Gauge, result: AnyRef, obj: AnyRef): Unit = result match {
    case number: Number ⇒ Kamon(UserMetrics).gauge(GaugeKey(gauge.name()), gauge.collector().newInstance()).record(number.longValue())
    case anythingElse   ⇒ // do nothing
  }
}

object AnnotationBla {
  val system: ActorSystem = ActorSystem("annotations-spec", ConfigFactory.parseString(
    """
      |kamon.metrics {
      |  default-collection-context-buffer-size = 100
      |}
    """.stripMargin))
}