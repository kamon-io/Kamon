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
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SingleLengthIdentifierSchemeSpec extends AnyWordSpec with Matchers with OptionValues {

  validateFactory("trace identifier factory", Identifier.Scheme.Single.traceIdFactory)
  validateFactory("span identifier factory", Identifier.Scheme.Single.spanIdFactory)

  def validateFactory(generatorName: String, factory: Identifier.Factory) = {
    s"The $generatorName" should {
      "generate random longs (8 byte) identifiers" in {
        100 times {
          val Identifier(string, bytes) = factory.generate()

          string.length should be(16)
          bytes.length should be(8)
        }
      }

      "decode the string representation back into a identifier" in {
        100 times {
          val identifier = factory.generate()
          val decodedIdentifier = factory.from(identifier.string)

          identifier.string should equal(decodedIdentifier.string)
          identifier.bytes should equal(decodedIdentifier.bytes)
        }
      }

      "decode the bytes representation back into a identifier" in {
        100 times {
          val identifier = factory.generate()
          val decodedIdentifier = factory.from(identifier.bytes)

          identifier.string should equal(decodedIdentifier.string)
          identifier.bytes should equal(decodedIdentifier.bytes)
        }
      }

      "return IdentityProvider.NoIdentifier if the provided input cannot be decoded into a Identifier" in {
        factory.from("zzzz") shouldBe (Identifier.Empty)
        factory.from(Array[Byte](1)) shouldBe (Identifier.Empty)
      }
    }
  }
}
