/* =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
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

/**
  * Inter-process Context propagation interface. Implementations are responsible from reading and writing a lossless
  * representation of a given Context instance to the appropriate mediums. Out of the box, Kamon ships with two
  * implementations:
  *
  *   * HttpPropagation: Uses HTTP headers as the medium to transport the Context data.
  *   * BinaryPropagation: Uses byte streams as the medium to transport the Context data.
  *
  */
trait Propagation[ReaderMedium, WriterMedium] {

  /**
    * Attempts to read a Context from the [[ReaderMedium]].
    *
    * @param medium An abstraction the reads data from the medium transporting the Context data.
    * @return The decoded Context instance or an empty Context if no entries or tags could be read from the medium.
    */
  def read(medium: ReaderMedium): Context

  /**
    * Attempts to write a Context instance to the [[WriterMedium]].
    *
    * @param context Context instance to be written.
    * @param medium An abstraction that writes data into the medium that will transport the Context data.
    */
  def write(context: Context, medium: WriterMedium): Unit

}

object Propagation {

  /**
    * Encapsulates logic required to read a single context entry from a medium. Implementations of this trait
    * must be aware of the entry they are able to read.
    */
  trait EntryReader[Medium] {

    /**
      * Tries to read a context entry from the medium. If a context entry is successfully read, implementations
      * must return an updated context instance that includes such entry. If no entry could be read simply return the
      * context instance that was passed in, unchanged.
      *
      * @param medium  An abstraction the reads data from the medium transporting the Context data.
      * @param context Current context.
      * @return Either the original context passed in or a modified version of it, including the read entry.
      */
    def read(medium: Medium, context: Context): Context
  }

  /**
    * Encapsulates logic required to write a single context entry to the medium. Implementations of this trait
    * must be aware of the entry they are able to write.
    */
  trait EntryWriter[Medium] {

    /**
      * Tries to write a context entry into the medium.
      *
      * @param context   The context from which entries should be written.
      * @param medium    An abstraction that writes data into the medium that will transport the Context data.
      */
    def write(context: Context, medium: Medium): Unit
  }
}
