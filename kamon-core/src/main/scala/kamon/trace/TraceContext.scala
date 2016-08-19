/*
 * =========================================================================================
 * Copyright © 2013-2016 the kamon project <http://kamon.io/>
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
import java.util

import kamon.trace.Status.Closed
import kamon.trace.TraceContextAware.DefaultTraceContextAware
import kamon.util.{ Function, RelativeNanoTimestamp, SameThreadExecutionContext, Supplier }

import scala.concurrent.Future

trait TraceContext {
  def name: String
  def token: String
  def tags: Map[String, String]
  def isEmpty: Boolean
  def nonEmpty: Boolean = !isEmpty
  def isClosed: Boolean = !(Status.Open == status)
  def status: Status
  def finish(): Unit
  def finishWithError(cause: Throwable): Unit
  def rename(newName: String): Unit
  def startSegment(segmentName: String, category: String, library: String): Segment
  def startSegment(segmentName: String, category: String, library: String, tags: Map[String, String]): Segment
  def addMetadata(key: String, value: String)
  def addTag(key: String, value: String): Unit
  def removeTag(key: String, value: String): Boolean
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
    withNewSegment(segmentName, category, library, Map.empty[String, String])(code)
  }

  def withNewSegment[T](segmentName: String, category: String, library: String, tags: Map[String, String])(code: ⇒ T): T = {
    val segment = startSegment(segmentName, category, library, tags)
    try code finally segment.finish()
  }

  def withNewAsyncSegment[T](segmentName: String, category: String, library: String)(code: ⇒ Future[T]): Future[T] = {
    withNewAsyncSegment(segmentName, category, library, Map.empty[String, String])(code)
  }

  def withNewAsyncSegment[T](segmentName: String, category: String, library: String, tags: Map[String, String])(code: ⇒ Future[T]): Future[T] = {
    val segment = startSegment(segmentName, category, library, tags)
    val result = code
    result.onComplete(_ ⇒ segment.finish())(SameThreadExecutionContext)
    result
  }

  // Java variant.
  def withNewSegment[T](segmentName: String, category: String, library: String, code: Supplier[T]): T =
    withNewSegment(segmentName, category, library)(code.get)

  def withNewSegment[T](segmentName: String, category: String, library: String, tags: util.Map[String, String], code: Supplier[T]): T = {
    import scala.collection.JavaConverters._
    withNewSegment(segmentName, category, library, tags.asScala.toMap)(code.get)
  }
}

trait Segment {
  def name: String
  def category: String
  def library: String
  def tags: Map[String, String]
  def isEmpty: Boolean
  def nonEmpty: Boolean = !isEmpty
  def isClosed: Boolean = !(Status.Open == status)
  def status: Status
  def finish(): Unit
  def finishWithError(cause: Throwable): Unit
  def rename(newName: String): Unit
  def addMetadata(key: String, value: String): Unit
  def addTag(key: String, value: String): Unit
  def removeTag(key: String, value: String): Boolean
}

case object EmptyTraceContext extends TraceContext {
  def name: String = "empty-trace"
  def token: String = ""
  def tags: Map[String, String] = Map.empty
  def isEmpty: Boolean = true
  def status: Status = Closed
  def finish(): Unit = {}
  def finishWithError(cause: Throwable): Unit = {}
  def rename(name: String): Unit = {}
  def startSegment(segmentName: String, category: String, library: String): Segment = EmptySegment
  def startSegment(segmentName: String, category: String, library: String, tags: Map[String, String]): Segment = EmptySegment
  def addMetadata(key: String, value: String): Unit = {}
  def startTimestamp = new RelativeNanoTimestamp(0L)
  def addTag(key: String, value: String): Unit = {}
  def removeTag(key: String, value: String): Boolean = false

  case object EmptySegment extends Segment {
    val name: String = "empty-segment"
    val category: String = "empty-category"
    val library: String = "empty-library"
    def tags: Map[String, String] = Map.empty
    def isEmpty: Boolean = true
    def status: Status = Closed
    def finish(): Unit = {}
    def finishWithError(cause: Throwable): Unit = {}
    def rename(newName: String): Unit = {}
    def addMetadata(key: String, value: String): Unit = {}
    def addTag(key: String, value: String): Unit = {}
    def removeTag(key: String, value: String): Boolean = false
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

sealed trait Status
object Status {
  case object Open extends Status
  case object Closed extends Status
  case object FinishedWithError extends Status
  case object FinishedSuccessfully extends Status
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
  @volatile @transient var segment: Segment = EmptyTraceContext.EmptySegment
}

object SegmentAware {
  def default: SegmentAware = new DefaultSegmentAware
  class DefaultSegmentAware extends DefaultTraceContextAware with SegmentAware {}
}