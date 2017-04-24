package kamon.util

trait MeasurementUnit {
  def dimension: Dimension
  def magnitude: Magnitude
}

trait Magnitude {
  def name: String
}

trait Dimension {
  def name: String
  def scale(value: Long, from: Magnitude, to: Magnitude): Double
}
