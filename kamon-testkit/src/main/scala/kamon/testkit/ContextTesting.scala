package kamon.testkit

import kamon.context.{Context, Key}

trait ContextTesting {
  val TestLocalKey = Key.local[Option[String]]("test-local-key", None)
  val TestBroadcastKey = Key.broadcast[Option[String]]("test-local-key", None)

  def contextWithLocal(value: String): Context =
    Context.create(TestLocalKey, Some(value))
}
