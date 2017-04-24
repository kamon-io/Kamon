package kamon.util

trait PathFilter {
  def accept(path: String): Boolean
}
