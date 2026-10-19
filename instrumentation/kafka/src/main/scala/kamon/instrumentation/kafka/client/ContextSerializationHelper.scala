/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon.instrumentation.kafka.client

import java.io.ByteArrayOutputStream

import kamon.Kamon
import kamon.context.{BinaryPropagation, Context}

/**
  * This helper function encapsulates the access to the nested Scala objects for usage in Java.
  *
  * Avoid ugly Java statements like this:
  *  Kamon.defaultBinaryPropagation().write(ctx, kamon.context.BinaryPropagation$ByteStreamWriter$.MODULE$.of(out));
  */
object ContextSerializationHelper {

  def toByteArray(ctx: Context): Array[Byte] = {
    val out = new ByteArrayOutputStream();
    Kamon.defaultBinaryPropagation().write(ctx, BinaryPropagation.ByteStreamWriter.of(out))
    out.toByteArray
  }

  def fromByteArray(input: Array[Byte]): Context = {
    Kamon.defaultBinaryPropagation().read(BinaryPropagation.ByteStreamReader.of(input))
  }
}
