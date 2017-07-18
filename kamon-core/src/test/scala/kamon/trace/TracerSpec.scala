package kamon.trace

import kamon.Kamon
import org.scalatest.{Matchers, WordSpec}

class TracerSpec extends WordSpec with Matchers {

  "the Kamon tracer" should {
    "build spans that contain all information given to the builder" in {
      val span = tracer.buildSpan("myOperation")
        .withSpanTag("hello", "world")
        .start()
    }
  }

  val tracer: Tracer = Kamon

}
