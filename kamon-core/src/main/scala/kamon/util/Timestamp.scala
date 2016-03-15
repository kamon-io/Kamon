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

package kamon.util

/**
 *  Epoch time stamp.
 */
class Timestamp(val seconds: Long) extends AnyVal {
  def <(that: Timestamp): Boolean = this.seconds < that.seconds
  def >(that: Timestamp): Boolean = this.seconds > that.seconds
  def ==(that: Timestamp): Boolean = this.seconds == that.seconds
  def >=(that: Timestamp): Boolean = this.seconds >= that.seconds
  def <=(that: Timestamp): Boolean = this.seconds <= that.seconds

  override def toString: String = String.valueOf(seconds) + ".seconds"
}

object Timestamp {
  def now: Timestamp = new Timestamp(System.currentTimeMillis() / 1000)
  def earlier(l: Timestamp, r: Timestamp): Timestamp = if (l <= r) l else r
  def later(l: Timestamp, r: Timestamp): Timestamp = if (l >= r) l else r
}

/**
 *  Epoch time stamp in milliseconds.
 */
class MilliTimestamp(val millis: Long) extends AnyVal {
  override def toString: String = String.valueOf(millis) + ".millis"

  def toTimestamp: Timestamp = new Timestamp(millis / 1000)
  def toRelativeNanoTimestamp: RelativeNanoTimestamp = {
    val diff = (System.currentTimeMillis() - millis) * 1000000
    new RelativeNanoTimestamp(System.nanoTime() - diff)
  }
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
  def -(that: NanoTimestamp) = new NanoTimestamp(nanos - that.nanos)
  def +(that: NanoTimestamp) = new NanoTimestamp(nanos + that.nanos)
  override def toString: String = String.valueOf(nanos) + ".nanos"
}

object NanoTimestamp {
  def now: NanoTimestamp = new NanoTimestamp(System.currentTimeMillis() * 1000000)
}

/**
 *  Number of nanoseconds between a arbitrary origin timestamp provided by the JVM via System.nanoTime()
 */
class RelativeNanoTimestamp(val nanos: Long) extends AnyVal {
  def -(that: RelativeNanoTimestamp) = new RelativeNanoTimestamp(nanos - that.nanos)
  def +(that: RelativeNanoTimestamp) = new RelativeNanoTimestamp(nanos + that.nanos)
  override def toString: String = String.valueOf(nanos) + ".nanos"

  def toMilliTimestamp: MilliTimestamp =
    new MilliTimestamp(System.currentTimeMillis - ((System.nanoTime - nanos) / 1000000))
}

object RelativeNanoTimestamp {
  val zero = new RelativeNanoTimestamp(0L)

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
