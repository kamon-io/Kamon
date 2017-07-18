package kamon.trace

import kamon.trace.IdentityProvider.Identifier
import org.scalactic.TimesOnInt._
import org.scalatest.{Matchers, OptionValues, WordSpecLike}

class DoubleLengthTraceIdentityGeneratorSpec extends WordSpecLike with Matchers with OptionValues {
  val idProvider = IdentityProvider.DoubleSizeTraceID()
  val traceGenerator = idProvider.traceIdentifierGenerator()
  val spanGenerator = idProvider.spanIdentifierGenerator()

  "The DoubleSizeTraceID identity provider" when {
    "generating trace identifiers" should {
      "generate random longs (16 byte) identifiers" in {
        100 times {
          val Identifier(string, bytes) = traceGenerator.generate()

          string.length should be(32)
          bytes.length should be(16)
        }
      }

      "decode the string representation back into a identifier" in {
        100 times {
          val identifier = traceGenerator.generate()
          val decodedIdentifier = traceGenerator.from(identifier.string)

          identifier.string should equal(decodedIdentifier.string)
          identifier.bytes should equal(decodedIdentifier.bytes)
        }
      }

      "decode the bytes representation back into a identifier" in {
        100 times {
          val identifier = traceGenerator.generate()
          val decodedIdentifier = traceGenerator.from(identifier.bytes)

          identifier.string should equal(decodedIdentifier.string)
          identifier.bytes should equal(decodedIdentifier.bytes)
        }
      }

      "return IdentityProvider.NoIdentifier if the provided input cannot be decoded into a Identifier" in {
        traceGenerator.from("zzzz") shouldBe (IdentityProvider.NoIdentifier)
        traceGenerator.from(Array[Byte](1)) shouldBe (IdentityProvider.NoIdentifier)
      }
    }

    "generating span identifiers" should {
      "generate random longs (8 byte) identifiers" in {
        100 times {
          val Identifier(string, bytes) = spanGenerator.generate()

          string.length should be(16)
          bytes.length should be(8)
        }
      }

      "decode the string representation back into a identifier" in {
        100 times {
          val identifier = spanGenerator.generate()
          val decodedIdentifier = spanGenerator.from(identifier.string)

          identifier.string should equal(decodedIdentifier.string)
          identifier.bytes should equal(decodedIdentifier.bytes)
        }
      }

      "decode the bytes representation back into a identifier" in {
        100 times {
          val identifier = spanGenerator.generate()
          val decodedIdentifier = spanGenerator.from(identifier.bytes)

          identifier.string should equal(decodedIdentifier.string)
          identifier.bytes should equal(decodedIdentifier.bytes)
        }
      }

      "return IdentityProvider.NoIdentifier if the provided input cannot be decoded into a Identifier" in {
        spanGenerator.from("zzzz") shouldBe (IdentityProvider.NoIdentifier)
        spanGenerator.from(Array[Byte](1)) shouldBe (IdentityProvider.NoIdentifier)
      }
    }
  }

}
