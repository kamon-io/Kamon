package kamon.trace

import java.util.concurrent.ConcurrentLinkedQueue

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import kamon.Kamon
import kamon.metric.TraceMetrics.TraceMetricRecorder
import kamon.metric.{ MetricsExtension, TraceMetrics, Metrics }

import scala.annotation.tailrec

class MetricsOnlyContext(
  traceName: String,
  val token: String,
  izOpen: Boolean,
  val levelOfDetail: LevelOfDetail,
  val origin: TraceContextOrigin,
  nanoTimeztamp: Long,
  log: LoggingAdapter,
  metricsExtension: MetricsExtension,
  val system: ActorSystem)
    extends TraceContext {

  @volatile private var _name = traceName
  @volatile private var _isOpen = izOpen
  @volatile protected var _elapsedNanoTime = 0L

  private val _nanoTimestamp = nanoTimeztamp
  private val _finishedSegments = new ConcurrentLinkedQueue[SegmentLatencyData]()
  private[kamon] val traceLocalStorage: TraceLocalStorage = new TraceLocalStorage

  def rename(newName: String): Unit =
    if (isOpen) _name = newName
    else if (log.isWarningEnabled) log.warning("Can't rename trace from [{}] to [{}] because the trace is already closed.", name, newName)

  def name: String = _name
  def isEmpty: Boolean = false
  def isOpen: Boolean = _isOpen
  def nanoTimestamp: Long = _nanoTimestamp
  def elapsedNanoTime: Long = _elapsedNanoTime
  def addMetadata(key: String, value: String): Unit = {}

  def finish(): Unit = {
    _isOpen = false
    _elapsedNanoTime = System.nanoTime() - _nanoTimestamp
    val metricRecorder = metricsExtension.register(TraceMetrics(name), TraceMetrics.Factory)

    metricRecorder.map { traceMetrics ⇒
      traceMetrics.elapsedTime.record(elapsedNanoTime)
      drainFinishedSegments(traceMetrics)
    }
  }

  def startSegment(segmentName: String, category: String, library: String): Segment =
    new MetricsOnlySegment(segmentName, category, library)

  @tailrec private def drainFinishedSegments(metricRecorder: TraceMetricRecorder): Unit = {
    val segment = _finishedSegments.poll()
    if (segment != null) {
      metricRecorder.segmentRecorder(segment.identity).record(segment.duration)
      drainFinishedSegments(metricRecorder)
    }
  }

  protected def finishSegment(segmentName: String, category: String, library: String, duration: Long): Unit = {
    _finishedSegments.add(SegmentLatencyData(SegmentMetricIdentity(segmentName, category, library), duration))

    if (isClosed) {
      metricsExtension.register(TraceMetrics(name), TraceMetrics.Factory).map { traceMetrics ⇒
        drainFinishedSegments(traceMetrics)
      }
    }
  }

  class MetricsOnlySegment(segmentName: String, val category: String, val library: String) extends Segment {
    protected val segmentStartNanoTime = System.nanoTime()
    @volatile private var _segmentName = segmentName
    @volatile private var _elapsedNanoTime = 0L
    @volatile protected var _isOpen = true

    def name: String = _segmentName
    def isEmpty: Boolean = false
    def addMetadata(key: String, value: String): Unit = {}
    def isOpen: Boolean = _isOpen
    def elapsedNanoTime: Long = _elapsedNanoTime

    def rename(newName: String): Unit =
      if (isOpen) _segmentName = newName
      else if (log.isWarningEnabled) log.warning("Can't rename segment from [{}] to [{}] because the segment is already closed.", name, newName)

    def finish: Unit = {
      _isOpen = false
      _elapsedNanoTime = System.nanoTime() - segmentStartNanoTime

      finishSegment(name, category, library, elapsedNanoTime)
    }
  }
}