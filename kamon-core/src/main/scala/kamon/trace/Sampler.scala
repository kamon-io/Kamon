/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

import kamon.NanoInterval
import kamon.util.Sequencer
import scala.concurrent.forkjoin.ThreadLocalRandom

trait Sampler {
  def shouldTrace: Boolean
  def shouldReport(traceElapsedTime: NanoInterval): Boolean
}

object NoSampling extends Sampler {
  def shouldTrace: Boolean = false
  def shouldReport(traceElapsedTime: NanoInterval): Boolean = false
}

object SampleAll extends Sampler {
  def shouldTrace: Boolean = true
  def shouldReport(traceElapsedTime: NanoInterval): Boolean = true
}

class RandomSampler(chance: Int) extends Sampler {
  require(chance > 0, "kamon.trace.random-sampler.chance cannot be <= 0")
  require(chance <= 100, "kamon.trace.random-sampler.chance cannot be > 100")

  def shouldTrace: Boolean = ThreadLocalRandom.current().nextInt(100) <= chance
  def shouldReport(traceElapsedTime: NanoInterval): Boolean = true
}

class OrderedSampler(interval: Int) extends Sampler {
  import OrderedSampler._

  require(interval > 0, "kamon.trace.ordered-sampler.interval cannot be <= 0")
  assume(interval isPowerOfTwo, "kamon.trace.ordered-sampler.interval must be power of two")

  private val sequencer = Sequencer()

  def shouldTrace: Boolean = (sequencer.next() fastMod interval) == 0
  def shouldReport(traceElapsedTime: NanoInterval): Boolean = true
}

object OrderedSampler {
  implicit class EnhancedInt(i: Int) {
    def isPowerOfTwo = (i & (i - 1)) == 0
  }

  implicit class EnhancedLong(dividend: Long) {
    def fastMod(divisor: Int) = dividend & (divisor - 1)
  }
}

class ThresholdSampler(thresholdInNanoseconds: Long) extends Sampler {
  require(thresholdInNanoseconds > 0, "kamon.trace.threshold-sampler.minimum-elapsed-time cannot be <= 0")

  def shouldTrace: Boolean = true
  def shouldReport(traceElapsedTime: NanoInterval): Boolean = traceElapsedTime.nanos >= thresholdInNanoseconds
}

