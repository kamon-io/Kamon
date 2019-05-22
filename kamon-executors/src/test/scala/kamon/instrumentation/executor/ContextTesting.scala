package kamon.instrumentation.executor

import kamon.context.Context
import kamon.tag.TagSet

trait ContextTesting {
  val TestKey = "testkey"
  def testContext(value: String) = Context.of(TagSet.of(TestKey, value))
}
