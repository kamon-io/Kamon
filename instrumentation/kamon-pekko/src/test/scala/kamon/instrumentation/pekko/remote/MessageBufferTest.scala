package kamon.instrumentation.pekko.remote

import org.apache.pekko.actor.Actor
import org.apache.pekko.util.MessageBuffer
import kamon.Kamon
import kamon.context.Context
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MessageBufferTest extends AnyWordSpec with Matchers {

  "the MessageBuffer instrumentation" should {
    "remember the current context when appending message and apply it when foreach is called when used directly" in {
      val messageBuffer = MessageBuffer.empty
      val key = Context.key("some_key", "")

      Kamon.runWithContext(Context.of(key, "some_value")) {
        messageBuffer.append("scala", Actor.noSender)
      }

      Kamon.currentContext().get(key) shouldBe ""

      var iterated = false
      messageBuffer.foreach { (msg, ref) =>
        iterated = true
        Kamon.currentContext().get(key) shouldBe "some_value"
      }

      iterated shouldBe true

    }
  }

}
