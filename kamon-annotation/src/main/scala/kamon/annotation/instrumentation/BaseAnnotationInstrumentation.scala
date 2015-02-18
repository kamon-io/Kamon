package kamon.annotation.instrumentation

import java.lang.reflect.{ Method, Modifier }

import kamon.Kamon
import kamon.annotation._
import kamon.annotation.util.{ EnhancedELProcessor, ELProcessorPool }
import kamon.metric.instrument.Histogram.DynamicRange
import kamon.metric.{ HistogramKey, MinMaxCounterKey, CounterKey, instrument }
import kamon.metric.instrument.{ MinMaxCounter, Counter }
import kamon.trace.TraceContext
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.{ DeclareMixin, Aspect, After, Pointcut }

import scala.collection.concurrent.TrieMap

trait BaseAnnotationInstrumentation {

  type TagsEvaluator = String ⇒ Map[String, String]
  type StringEvaluator = String ⇒ String

  private[instrumentation] def registerTime(method: Method, histograms: TrieMap[String, instrument.Histogram], eval: StringEvaluator, tags: TagsEvaluator): Any = {
    if (method.isAnnotationPresent(classOf[Time])) {
      val time = method.getAnnotation(classOf[Time])
      val name = eval(time.name())
      val metadata = tags(time.tags())
      val currentHistogram = Kamon.simpleMetrics.histogram(HistogramKey(name, metadata))
      histograms.put(method.getName, currentHistogram)
    }
  }

  private[instrumentation] def registerHistogram(method: Method, histograms: TrieMap[String, instrument.Histogram], eval: StringEvaluator, tags: TagsEvaluator): Unit = {
    if (method.isAnnotationPresent(classOf[Histogram])) {
      val histogram = method.getAnnotation(classOf[Histogram])
      val name = eval(histogram.name())
      val metadata = tags(histogram.tags())
      val dynamicRange = DynamicRange(histogram.lowestDiscernibleValue(), histogram.highestTrackableValue(), histogram.precision())
      val currentHistogram = Kamon.simpleMetrics.histogram(HistogramKey(name, metadata), dynamicRange)
      histograms.put(method.getName, currentHistogram)
    }
  }

  private[instrumentation] def registerMinMaxCounter(method: Method, minMaxCounters: TrieMap[String, MinMaxCounter], eval: StringEvaluator, tags: TagsEvaluator): Unit = {
    if (method.isAnnotationPresent(classOf[MinMaxCount])) {
      val minMaxCount = method.getAnnotation(classOf[MinMaxCount])
      val name = eval(minMaxCount.name())
      val metadata = tags(minMaxCount.tags())
      val minMaxCounter = Kamon.simpleMetrics.minMaxCounter(MinMaxCounterKey(name, metadata))
      minMaxCounters.put(method.getName, minMaxCounter)
    }
  }

  private[instrumentation] def registerCounter(method: Method, counters: TrieMap[String, Counter], eval: StringEvaluator, tags: TagsEvaluator): Unit = {
    if (method.isAnnotationPresent(classOf[Count])) {
      val count = method.getAnnotation(classOf[Count])
      val name = eval(count.name())
      val metadata = tags(count.tags())
      val counter = Kamon.simpleMetrics.counter(CounterKey(name, metadata))
      counters.put(method.getName, counter)
    }
  }

  private[instrumentation] def registerTrace(method: Method, traces: TrieMap[String, TraceContext], eval: StringEvaluator, tags: TagsEvaluator): Unit = {
    if (method.isAnnotationPresent(classOf[Trace])) {
      val count = method.getAnnotation(classOf[Trace])
      val name = eval(count.value())
      val metadata = tags(count.tags())
      val trace = Kamon.tracer.newContext(name)
      metadata.foreach { case (key, value) ⇒ trace.addMetadata(key, value) }
      traces.put(method.getName, trace)
    }
  }

  private[instrumentation] def registerSegment(method: Method, segments: TrieMap[String, SegmentInfo], eval: StringEvaluator, tags: TagsEvaluator): Unit = {
    if (method.isAnnotationPresent(classOf[Segment])) {
      val segment = method.getAnnotation(classOf[Segment])
      val name = eval(segment.name())
      val category = eval(segment.category())
      val library = eval(segment.library())
      val metadata = tags(segment.tags())

      segments.put(method.getName, SegmentInfo(name, category, library, metadata))
    }
  }
}

@Aspect
class ClassToProfiledMixin {
  @DeclareMixin("(@kamon.annotation.Metrics *)")
  def mixinActorSystemAwareToDispatchers: Profiled = new Profiled {}
}

trait Profiled {
  val traces = TrieMap[String, TraceContext]()
  val segments = TrieMap[String, SegmentInfo]()
  val counters = TrieMap[String, Counter]()
  val minMaxCounters = TrieMap[String, MinMaxCounter]()
  val histograms = TrieMap[String, instrument.Histogram]()
}

case class SegmentInfo(name: String, category: String, library: String, tags: Map[String, String])
