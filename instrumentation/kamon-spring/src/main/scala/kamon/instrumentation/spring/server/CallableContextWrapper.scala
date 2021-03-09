package kamon.instrumentation.spring.server

import kamon.Kamon

import java.util.concurrent.Callable

object CallableContextWrapper {

  def wrap[T](callable: Callable[T]): Callable[T] = new Callable[T] {
    private val _context = Kamon.currentContext()

    override def call(): T = {
      val scope = Kamon.storeContext(_context)
      try { callable.call() } finally {
        scope.close()
      }
    }
  }
}
