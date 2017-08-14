package kamon.context

import kamon.Kamon
import org.scalatest.{Matchers, WordSpec}

class ContextCodecSpec extends WordSpec with Matchers {
  "the Context Codec" when {
    "encoding/decoding to HttpHeaders" should {
      "encode stuff" in {



      }
    }
  }

  val ContextCodec = new Codec(Kamon.identityProvider, Kamon.config())
}
