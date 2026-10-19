/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon.util

import kamon.metric.MeasurementUnit.Dimension
import kamon.metric.{Distribution, DynamicRange, MeasurementUnit}

/**
  * Utility class that converts values and distributions to a desired unit. This class provides a convenient way to
  * keep fixed preferences regarding what time and information units to expect on the output and apply those conversion
  * when possible.
  */
class UnitConverter(
  targetTimeUnit: MeasurementUnit,
  targetInformationUnit: MeasurementUnit,
  dynamicRange: DynamicRange
) {

  /**
    * Tries to convert a value from its unit to the appropriate time/information unit set when creating this
    * UnitConverter. If the value unit's dimension is not time or information then values will be returned unchanged.
    */
  def convertValue(value: Double, unit: MeasurementUnit): Double =
    if (unit.dimension == Dimension.Time)
      MeasurementUnit.convert(value, unit, targetTimeUnit)
    else if (unit.dimension == Dimension.Information)
      MeasurementUnit.convert(value, unit, targetInformationUnit)
    else
      value

  /**
    * Tries to convert a Distribution from its unit and dynamic range to the appropriate time/information unit set when
    * creating this UnitConverter. If the Distribution unit's dimension is not time or information then values will only
    * be converted to a different dynamic range, if necessary.
    */
  def convertDistribution(distribution: Distribution, unit: MeasurementUnit): Distribution =
    if (unit.dimension == Dimension.Time)
      Distribution.convert(distribution, unit, targetTimeUnit, dynamicRange)
    else if (unit.dimension == Dimension.Information)
      Distribution.convert(distribution, unit, targetInformationUnit, dynamicRange)
    else
      Distribution.convert(distribution, unit, unit, dynamicRange)

}
