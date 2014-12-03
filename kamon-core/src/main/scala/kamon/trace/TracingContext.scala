package kamon.trace

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.{ AtomicInteger, AtomicLongFieldUpdater }

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import kamon.Kamon
import kamon.metric.TraceMetrics.TraceMetricRecorder
import kamon.metric.{ MetricsExtension, Metrics, TraceMetrics }

import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap

class TracingContext(traceName: String, token: String, izOpen: Boolean, levelOfDetail: LevelOfDetail, origin: TraceContextOrigin,
  nanoTimeztamp: Long, log: LoggingAdapter, traceExtension: TraceExtension, metricsExtension: MetricsExtension, system: ActorSystem)
    extends MetricsOnlyContext(traceName, token, izOpen, levelOfDetail, origin, nanoTimeztamp, log, metricsExtension, system) {

  val openSegments = new AtomicInteger(0)
  private val startMilliTime = System.currentTimeMillis()
  private val allSegments = new ConcurrentLinkedQueue[TracingSegment]()
  private val metadata = TrieMap.empty[String, String]

  override def addMetadata(key: String, value: String): Unit = metadata.put(key, value)

  override def startSegment(segmentName: String, category: String, library: String): Segment = {
    openSegments.incrementAndGet()
    val newSegment = new TracingSegment(segmentName, category, library)
    allSegments.add(newSegment)
    newSegment
  }

  override def finish(): Unit = {
    super.finish()
    traceExtension.report(this)
  }

  override def finishSegment(segmentName: String, category: String, library: String, duration: Long): Unit = {
    openSegments.decrementAndGet()
    super.finishSegment(segmentName, category, library, duration)
  }

  def shouldIncubate: Boolean = isOpen || openSegments.get() > 0

  def generateTraceInfo: Option[TraceInfo] = if (isOpen) None else {
    val currentSegments = allSegments.iterator()
    var segmentsInfo: List[SegmentInfo] = Nil

    while (currentSegments.hasNext()) {
      val segment = currentSegments.next()
      segment.createSegmentInfo match {
        case Some(si) ⇒ segmentsInfo = si :: segmentsInfo
        case None     ⇒ log.warning("Segment [{}] will be left out of TraceInfo because it was still open.", segment.name)
      }
    }

    Some(TraceInfo(traceName, token, startMilliTime, nanoTimeztamp, elapsedNanoTime, metadata.toMap, segmentsInfo))
  }

  class TracingSegment(segmentName: String, category: String, library: String) extends MetricsOnlySegment(segmentName, category, library) {
    private val metadata = TrieMap.empty[String, String]
    override def addMetadata(key: String, value: String): Unit = metadata.put(key, value)

    override def finish: Unit = {
      super.finish()
    }

    def createSegmentInfo: Option[SegmentInfo] =
      if (isOpen) None
      else Some(SegmentInfo(this.name, category, library, segmentStartNanoTime, elapsedNanoTime, metadata.toMap))
  }
}