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

package kamon.context

/**
  * Inter-process Context propagation interface. Implementations are responsible from reading and writing a lossless
  * representation of a given Context instance to the appropriate mediums. Out of the box, Kamon ships with two
  * implementations:
  *
  *   - HttpPropagation: Uses HTTP headers as the medium to transport the Context data.
  *   - BinaryPropagation: Uses byte streams as the medium to transport the Context data.
  *
  */
trait Propagation[ReaderMedium, WriterMedium] {

  /**
    * Reads a Context from a ReaderMedium instance. If there is any problem while reading the Context then Context.Empty
    * should be returned instead of throwing any exceptions.
    */
  def read(medium: ReaderMedium): Context

  /**
    * Attempts to write a Context instance to the WriterMedium.
    */
  def write(context: Context, medium: WriterMedium): Unit

}

object Propagation {

  /**
    * Encapsulates logic required to read a single Context entry from a medium. Implementations of this trait must be
    * aware of the entry they are able to read.
    */
  trait EntryReader[Medium] {

    /**
      * Tries to read a Context entry from the medium. If a Context entry is successfully read, implementations must
      * return an updated Context instance that includes such entry. If no entry could be read simply return the Context
      * instance that was passed in, unchanged.
      */
    def read(medium: Medium, context: Context): Context
  }

  /**
    * Encapsulates logic required to write a single Context entry to the medium. Implementations of this trait must be
    * aware of the entry they are able to write.
    */
  trait EntryWriter[Medium] {

    /**
      * Tries to write an entry from the provided Context into the medium.
      */
    def write(context: Context, medium: Medium): Unit
  }
}
