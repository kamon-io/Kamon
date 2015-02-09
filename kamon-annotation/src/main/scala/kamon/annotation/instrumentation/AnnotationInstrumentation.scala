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
import kamon.annotation.util.ELProcessorPool
import kamon.annotation.{ Counted, _ }
import kamon.metric._
import kamon.metric.instrument.Gauge.CurrentValueCollector
import kamon.metric.instrument.{ Counter, MinMaxCounter }
import kamon.trace.{ TraceContext, Tracer }
import kamon.util.Latency
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.{ After, AfterReturning, Around, Aspect }

@Aspect
class AnnotationInstrumentation extends InstrumentsAware {

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
    Latency.measure(histogram(timed.name(), timed.metadata())(obj))(pjp.proceed)
  }

  @After("execution(@kamon.annotation.Counted * *(..)) && @annotation(counted) && this(obj)")
  def counted(counted: Counted, obj: AnyRef): Unit = counted.`type`() match {
    case CounterType.Counter       ⇒ counter(counted.name(), counted.metadata())(obj).increment()
    case CounterType.MinMaxCounter ⇒ minMaxCounter(counted.name(), counted.metadata())(obj).increment()
  }

  @AfterReturning(pointcut = "execution(@kamon.annotation.Gauge * *(..)) && @annotation(g) && this(obj)", returning = "result")
  def gauge(g: Gauge, result: AnyRef, obj: AnyRef): Unit = result match {
    case number: Number ⇒ gauge(g.name(), g.collector().newInstance(), g.metadata())(obj).record(number.longValue())
    case anythingElse   ⇒ // do nothing
  }
}

trait InstrumentsAware {
  this: AnnotationInstrumentation ⇒

  def counter(name: String, metadata: String)(obj: AnyRef): Counter = Kamon(UserMetrics).counter(CounterKey(resolveName(name, obj), resolveMetadata(metadata)))
  def minMaxCounter(name: String, metadata: String)(obj: AnyRef): MinMaxCounter = Kamon(UserMetrics).minMaxCounter(MinMaxCounterKey(resolveName(name, obj), resolveMetadata(metadata)))
  def gauge(name: String, valueCollector: CurrentValueCollector, metadata: String)(obj: AnyRef): instrument.Gauge = Kamon(UserMetrics).gauge(GaugeKey(resolveName(name, obj), resolveMetadata(metadata)), valueCollector)
  def histogram(name: String, metadata: String)(obj: AnyRef): instrument.Histogram = Kamon(UserMetrics).histogram(HistogramKey(resolveName(name, obj), resolveMetadata(metadata)))

  private def resolveName(name: String, obj: AnyRef): String = ELProcessorPool.useWithObject(obj)(processor ⇒ processor.evalToString(name))
  private def resolveMetadata(expression: String): Map[String, String] = ELProcessorPool.use(processor ⇒ processor.evalToMap(expression)).toMap
}

object AnnotationBla {
  val system: ActorSystem = ActorSystem("annotations-spec", ConfigFactory.parseString(
    """
      |kamon.metrics {
      |  default-collection-context-buffer-size = 100
      |}
    """.stripMargin))
}