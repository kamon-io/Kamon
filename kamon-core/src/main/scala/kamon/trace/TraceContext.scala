/*
 * =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
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

package kamon.trace

import java.io.ObjectStreamException
import kamon.trace.TraceContextAware.DefaultTraceContextAware
import kamon.util.{ SameThreadExecutionContext, Supplier, Function, RelativeNanoTimestamp }

import scala.concurrent.Future

trait TraceContext {
  def name: String
  def token: String
  def isEmpty: Boolean
  def nonEmpty: Boolean = !isEmpty
  def isOpen: Boolean
  def isClosed: Boolean = !isOpen

  def finish(): Unit
  def rename(newName: String): Unit

  def startSegment(segmentName: String, category: String, library: String): Segment
  def addMetadata(key: String, value: String)

  def startTimestamp: RelativeNanoTimestamp

  def collect[T](f: TraceContext ⇒ T): Option[T] =
    if (nonEmpty)
      Some(f(this))
    else None

  def collect[T](f: Function[TraceContext, T]): Option[T] =
    if (nonEmpty)
      Some(f(this))
    else None

  def withNewSegment[T](segmentName: String, category: String, library: String)(code: ⇒ T): T = {
    val segment = startSegment(segmentName, category, library)
    try code finally segment.finish()
  }

  // Java variant.
  def withNewSegment[T](segmentName: String, category: String, library: String, code: Supplier[T]): T =
    withNewSegment(segmentName, category, library)(code.get)

  def withNewAsyncSegment[T](segmentName: String, category: String, library: String)(code: ⇒ Future[T]): Future[T] = {
    val segment = startSegment(segmentName, category, library)
    val result = code
    code.onComplete(_ ⇒ segment.finish())(SameThreadExecutionContext)
    result
  }

}

trait Segment {
  def name: String
  def category: String
  def library: String
  def isEmpty: Boolean
  def nonEmpty: Boolean = !isEmpty
  def isOpen: Boolean
  def isClosed: Boolean = !isOpen

  def finish(): Unit
  def rename(newName: String): Unit
  def addMetadata(key: String, value: String)
}

case object EmptyTraceContext extends TraceContext {
  def name: String = "empty-trace"
  def token: String = ""
  def isEmpty: Boolean = true
  def isOpen: Boolean = false

  def finish(): Unit = {}
  def rename(name: String): Unit = {}
  def startSegment(segmentName: String, category: String, library: String): Segment = EmptySegment
  def addMetadata(key: String, value: String): Unit = {}
  def startTimestamp = new RelativeNanoTimestamp(0L)

  case object EmptySegment extends Segment {
    val name: String = "empty-segment"
    val category: String = "empty-category"
    val library: String = "empty-library"
    def isEmpty: Boolean = true
    def isOpen: Boolean = false

    def finish: Unit = {}
    def rename(newName: String): Unit = {}
    def addMetadata(key: String, value: String): Unit = {}
  }
}

object SegmentCategory {
  val HttpClient = "http-client"
  val Database = "database"
}

class LOD private[trace] (val level: Int) extends AnyVal
object LOD {
  val MetricsOnly = new LOD(1)
  val SimpleTrace = new LOD(2)
}

sealed trait LevelOfDetail
object LevelOfDetail {
  case object MetricsOnly extends LevelOfDetail
  case object SimpleTrace extends LevelOfDetail
  case object FullTrace extends LevelOfDetail
}

trait TraceContextAware extends Serializable {
  def traceContext: TraceContext
}

object TraceContextAware {
  def default: TraceContextAware = new DefaultTraceContextAware

  class DefaultTraceContextAware extends TraceContextAware {
    @transient val traceContext = Tracer.currentContext

    //
    // Beware of this hack, it might bite us in the future!
    //
    // When using remoting/cluster all messages carry the TraceContext in the envelope in which they
    // are sent but that doesn't apply to System Messages. We are certain that the TraceContext is
    // available (if any) when the system messages are read and this will make sure that it is correctly
    // captured and propagated.
    @throws[ObjectStreamException]
    private def readResolve: AnyRef = {
      new DefaultTraceContextAware
    }
  }
}

trait TimestampedTraceContextAware extends TraceContextAware {
  def captureNanoTime: Long
}

object TimestampedTraceContextAware {
  def default: TimestampedTraceContextAware = new DefaultTraceContextAware with TimestampedTraceContextAware {
    @transient val captureNanoTime = System.nanoTime()
  }
}

trait SegmentAware {
  @volatile var segment: Segment = EmptyTraceContext.EmptySegment
}

object SegmentAware {
  def default: SegmentAware = new DefaultSegmentAware
  class DefaultSegmentAware extends DefaultTraceContextAware with SegmentAware {}
}