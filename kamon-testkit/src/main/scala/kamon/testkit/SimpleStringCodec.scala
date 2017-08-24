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

package kamon.testkit

import java.nio.ByteBuffer

import kamon.context.{Codecs, Context, TextMap}

object SimpleStringCodec {
  final class Headers extends Codecs.ForEntry[TextMap] {
    private val dataKey = "X-String-Value"

    override def encode(context: Context): TextMap = {
      val textMap = TextMap.Default()
      context.get(ContextTesting.StringBroadcastKey).foreach { value =>
        textMap.put(dataKey, value)
      }

      textMap
    }

    override def decode(carrier: TextMap, context: Context): Context = {
      carrier.get(dataKey) match {
        case value @ Some(_) => context.withKey(ContextTesting.StringBroadcastKey, value)
        case None            => context
      }
    }
  }

  final class Binary extends Codecs.ForEntry[ByteBuffer] {
    val emptyBuffer: ByteBuffer = ByteBuffer.allocate(0)

    override def encode(context: Context): ByteBuffer = {
      context.get(ContextTesting.StringBroadcastKey) match {
        case Some(value)  => ByteBuffer.wrap(value.getBytes)
        case None         => emptyBuffer
      }
    }

    override def decode(carrier: ByteBuffer, context: Context): Context = {
      context.withKey(ContextTesting.StringBroadcastKey, Some(new String(carrier.array())))
    }
  }
}
