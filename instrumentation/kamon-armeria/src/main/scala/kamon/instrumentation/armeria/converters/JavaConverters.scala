package kamon.instrumentation.armeria.converters

import java.util.function.{Function => JFunction}

/**
  * Only because scala-2.11 compatibility issues
  */
trait JavaConverters {
  implicit def toJavaFunction[A, B](f: Function1[A, B]): JFunction[A, B] = new JFunction[A, B] {
    override def apply(a: A): B = f(a)
  }
}
