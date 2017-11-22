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

package kamon.context

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}

import kamon.Kamon
import kamon.testkit.ContextTesting
import org.scalatest.{Matchers, OptionValues, WordSpec}

class ContextSerializationSpec extends WordSpec with Matchers with ContextTesting with OptionValues {
  "the Context is Serializable" should {
    "empty " in {
      val bos = new ByteArrayOutputStream()
      val oos = new ObjectOutputStream(bos)
      oos.writeObject(Context.Empty)

      val ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray))
      val ctx = ois.readObject().asInstanceOf[Context]
      ctx shouldBe Context.Empty
    }

    "full" in {
      val sCtx = Context(StringBroadcastKey, Some("disi"))
      val bos = new ByteArrayOutputStream()
      val oos = new ObjectOutputStream(bos)
      oos.writeObject(sCtx)

      val ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray))
      val rCtx = ois.readObject().asInstanceOf[Context]
      rCtx shouldBe sCtx
    }

  }

  val ContextCodec = new Codecs(Kamon.config())
}