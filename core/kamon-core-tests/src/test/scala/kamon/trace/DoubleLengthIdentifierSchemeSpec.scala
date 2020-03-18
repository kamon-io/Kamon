/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.trace

import org.scalactic.TimesOnInt._
import org.scalatest.{Matchers, OptionValues, WordSpecLike}

class DoubleLengthIdentifierSchemeSpec extends WordSpecLike with Matchers with OptionValues {
  import Identifier.Scheme.Double.{spanIdFactory, traceIdFactory}

  "The double length identifier scheme" when {
    "generating trace identifiers" should {
      "generate random longs (16 byte) identifiers" in {
        100 times {
          val Identifier(string, bytes) = traceIdFactory.generate()

          string.length should be(32)
          bytes.length should be(16)
        }
      }

      "decode the string representation back into a identifier" in {
        100 times {
          val identifier = traceIdFactory.generate()
          val decodedIdentifier = traceIdFactory.from(identifier.string)

          identifier.string should equal(decodedIdentifier.string)
          identifier.bytes should equal(decodedIdentifier.bytes)
        }
      }

      "decode the bytes representation back into a identifier" in {
        100 times {
          val identifier = traceIdFactory.generate()
          val decodedIdentifier = traceIdFactory.from(identifier.bytes)

          identifier.string should equal(decodedIdentifier.string)
          identifier.bytes should equal(decodedIdentifier.bytes)
        }
      }

      "return IdentityProvider.NoIdentifier if the provided input cannot be decoded into a Identifier" in {
        traceIdFactory.from("zzzz") shouldBe (Identifier.Empty)
        traceIdFactory.from(Array[Byte](1)) shouldBe (Identifier.Empty)
      }
    }

    "generating span identifiers" should {
      "generate random longs (8 byte) identifiers" in {
        100 times {
          val Identifier(string, bytes) = spanIdFactory.generate()

          string.length should be(16)
          bytes.length should be(8)
        }
      }

      "decode the string representation back into a identifier" in {
        100 times {
          val identifier = spanIdFactory.generate()
          val decodedIdentifier = spanIdFactory.from(identifier.string)

          identifier.string should equal(decodedIdentifier.string)
          identifier.bytes should equal(decodedIdentifier.bytes)
        }
      }

      "decode the bytes representation back into a identifier" in {
        100 times {
          val identifier = spanIdFactory.generate()
          val decodedIdentifier = spanIdFactory.from(identifier.bytes)

          identifier.string should equal(decodedIdentifier.string)
          identifier.bytes should equal(decodedIdentifier.bytes)
        }
      }

      "return IdentityProvider.NoIdentifier if the provided input cannot be decoded into a Identifier" in {
        spanIdFactory.from("zzzz") shouldBe (Identifier.Empty)
        spanIdFactory.from(Array[Byte](1)) shouldBe (Identifier.Empty)
      }
    }
  }

}
