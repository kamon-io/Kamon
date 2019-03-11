package kamon.akka

import kamon.context.Context
import kamon.tag.TagSet

object ContextTesting {
  val TestKey = "testkey"
  def testContext(value: String) = Context.of(TagSet.of(TestKey, value))
}
