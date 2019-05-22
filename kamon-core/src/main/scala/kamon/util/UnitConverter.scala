package kamon.util

import kamon.metric.MeasurementUnit.Dimension
import kamon.metric.{Distribution, DynamicRange, MeasurementUnit}

/**
  * Utility class that converts values and distributions to a desired unit. This class provides a convenient way to
  * keep fixed preferences regarding what time and information units to expect on the output and apply those conversion
  * when possible.
  */
class UnitConverter(targetTimeUnit: MeasurementUnit, targetInformationUnit: MeasurementUnit, dynamicRange: DynamicRange) {

  /**
    * Tries to convert a value from its unit to the appropriate time/information unit set when creating this
    * UnitConverter. If the value unit's dimension is not time or information then values will be returned unchanged.
    */
  def convertValue(value: Double, unit: MeasurementUnit): Double =
    if(unit.dimension == Dimension.Time)
      MeasurementUnit.convert(value, unit, targetTimeUnit)
    else if(unit.dimension == Dimension.Information)
      MeasurementUnit.convert(value, unit, targetInformationUnit)
    else
      value

  /**
    * Tries to convert a Distribution from its unit and dynamic range to the appropriate time/information unit set when
    * creating this UnitConverter. If the Distribution unit's dimension is not time or information then values will only
    * be converted to a different dynamic range, if necessary.
    */
  def convertDistribution(distribution: Distribution, unit: MeasurementUnit): Distribution =
    if(unit.dimension == Dimension.Time)
      Distribution.convert(distribution, unit, targetTimeUnit, dynamicRange)
    else if(unit.dimension == Dimension.Information)
      Distribution.convert(distribution, unit, targetInformationUnit, dynamicRange)
    else
      Distribution.convert(distribution, unit, unit, dynamicRange)

}
