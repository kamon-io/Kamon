package kamon.util

trait Supplier[T] {
  def get: T
}

trait Function[T, R] {
  def apply(t: T): R
}