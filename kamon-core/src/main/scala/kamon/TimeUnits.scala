package kamon

/**
 *  Epoch time stamp in milliseconds.
 */
class MilliTimestamp(val millis: Long) extends AnyVal {
  override def toString: String = String.valueOf(millis) + ".millis"
}

object MilliTimestamp {
  def now: MilliTimestamp = new MilliTimestamp(System.currentTimeMillis())
}

/**
 *  Epoch time stamp in nanoseconds.
 *
 *  NOTE: This doesn't have any better precision than MilliTimestamp, it is just a convenient way to get a epoch
 *  timestamp in nanoseconds.
 */
class NanoTimestamp(val nanos: Long) extends AnyVal {
  override def toString: String = String.valueOf(nanos) + ".nanos"
}

object NanoTimestamp {
  def now: NanoTimestamp = new NanoTimestamp(System.currentTimeMillis() * 1000000)
}

/**
 *  Number of nanoseconds between a arbitrary origin timestamp provided by the JVM via System.nanoTime()
 */
class RelativeNanoTimestamp(val nanos: Long) extends AnyVal {
  override def toString: String = String.valueOf(nanos) + ".nanos"
}

object RelativeNanoTimestamp {
  def now: RelativeNanoTimestamp = new RelativeNanoTimestamp(System.nanoTime())
  def relativeTo(milliTimestamp: MilliTimestamp): RelativeNanoTimestamp =
    new RelativeNanoTimestamp(now.nanos - (MilliTimestamp.now.millis - milliTimestamp.millis) * 1000000)
}

/**
 *  Number of nanoseconds that passed between two points in time.
 */
class NanoInterval(val nanos: Long) extends AnyVal {
  def <(that: NanoInterval): Boolean = this.nanos < that.nanos
  def >(that: NanoInterval): Boolean = this.nanos > that.nanos
  def ==(that: NanoInterval): Boolean = this.nanos == that.nanos
  def >=(that: NanoInterval): Boolean = this.nanos >= that.nanos
  def <=(that: NanoInterval): Boolean = this.nanos <= that.nanos

  override def toString: String = String.valueOf(nanos) + ".nanos"
}

object NanoInterval {
  def default: NanoInterval = new NanoInterval(0L)
  def since(relative: RelativeNanoTimestamp): NanoInterval = new NanoInterval(System.nanoTime() - relative.nanos)
}
