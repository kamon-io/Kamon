/*
 * =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon.statsd

import java.text.{DecimalFormat, DecimalFormatSymbols}
import java.util.Locale

trait StatsDValueFormatters {

  val symbols: DecimalFormatSymbols = DecimalFormatSymbols.getInstance(Locale.US)
  symbols.setDecimalSeparator('.')
  // Just in case there is some weird locale config we are not aware of.

  // Absurdly high number of decimal digits, let the other end lose precision if it needs to.
  val samplingRateFormat = new DecimalFormat("#.################################################################", symbols)

  def encodeStatsDTimer(level: Long, count: Long): String = {
    val samplingRate: Double = 1D / count
    level.toString + "|ms" + (if (samplingRate != 1D) "|@" + samplingRateFormat.format(samplingRate) else "")
  }

  def encodeStatsDCounter(count: Long): String = count.toString + "|c"
  def encodeStatsDGauge(value:Long): String = value.toString + "|g"
}


