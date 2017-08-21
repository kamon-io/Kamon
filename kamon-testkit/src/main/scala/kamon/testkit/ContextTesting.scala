package kamon.testkit

import kamon.context.{Context, Key}

trait ContextTesting {
  val StringKey = Key.local[Option[String]]("string-key", None)
  val StringBroadcastKey = Key.broadcast[Option[String]]("string-broadcast-key", None)

  def contextWithLocal(value: String): Context =
    Context.create(StringKey, Some(value))
}

object ContextTesting extends ContextTesting
