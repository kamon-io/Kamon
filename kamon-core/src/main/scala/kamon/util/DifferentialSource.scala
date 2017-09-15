package kamon.util

/**
  * Keeps track of the values produced by the source and produce the difference between the last two observed values
  * when calling get. This class assumes the source increases monotonically and any produced value that violates this
  * assumption will be dropped.
  *
  */
class DifferentialSource(source: () => Long) {
  private var previousValue = source()

  def get(): Long = synchronized {
    val currentValue = source()
    val diff = currentValue - previousValue
    previousValue = currentValue

    if(diff < 0) 0 else diff
  }
}

object DifferentialSource {
  def apply(source: () => Long): DifferentialSource =
    new DifferentialSource(source)
}
