package kamon.trace

import kamon.trace.IdentityProvider.Identifier
import org.scalatest.{Matchers, OptionValues, WordSpecLike}
import org.scalactic.TimesOnInt._

class DefaultIdentityGeneratorSpec extends WordSpecLike with Matchers with OptionValues {
  val idProvider = IdentityProvider.Default()
  val traceGenerator = idProvider.traceIdentifierGenerator()
  val spanGenerator = idProvider.spanIdentifierGenerator()

  validateGenerator("TraceID Generator", traceGenerator)
  validateGenerator("SpanID Generator", spanGenerator)

  def validateGenerator(generatorName: String, generator: IdentityProvider.Generator) = {
    s"The $generatorName" should {
      "generate random longs (8 byte) as Span and Trace identifiers" in {
        100 times {
          val Identifier(string, bytes) = generator.generate()

          string.length should be(16)
          bytes.length should be(8)
        }
      }

      "decode the string representation back into a identifier" in {
        100 times {
          val identifier = generator.generate()
          val decodedIdentifier = generator.from(identifier.string)

          identifier.string should equal(decodedIdentifier.string)
          identifier.bytes should equal(decodedIdentifier.bytes)
        }
      }

      "decode the bytes representation back into a identifier" in {
        100 times {
          val identifier = generator.generate()
          val decodedIdentifier = generator.from(identifier.bytes)

          identifier should equal(decodedIdentifier)
        }
      }
    }
  }
}
