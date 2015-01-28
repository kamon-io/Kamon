package kamon.system.sigar

import kamon.metric.GenericEntityRecorder
import kamon.metric.instrument.{ Memory, InstrumentFactory }
import org.hyperic.sigar.{ DiskUsage, FileSystem, Sigar }
import scala.util.Try

class FileSystemMetrics(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) with SigarMetric {
  val reads = DiffRecordingHistogram(histogram("file-system-reads", Memory.Bytes))
  val writes = DiffRecordingHistogram(histogram("file-system-writes", Memory.Bytes))

  def sumOfAllFileSystems(sigar: Sigar, thunk: DiskUsage ⇒ Long): Long = Try {
    val fileSystems = sigar.getFileSystemList.filter(_.getType == FileSystem.TYPE_LOCAL_DISK).map(_.getDevName).toSet
    fileSystems.map(i ⇒ thunk(sigar.getDiskUsage(i))).fold(0L)(_ + _)

  } getOrElse (0L)

  def update(sigar: Sigar): Unit = {
    reads.record(sumOfAllFileSystems(sigar, _.getReadBytes))
    writes.record(sumOfAllFileSystems(sigar, _.getWriteBytes))
  }
}

object FileSystemMetrics extends SigarMetricRecorderCompanion("file-system") {
  def apply(instrumentFactory: InstrumentFactory): FileSystemMetrics =
    new FileSystemMetrics(instrumentFactory)
}
