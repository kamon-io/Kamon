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

package kamon.instrumentation.http

import kamon.context.HttpPropagation.{HeaderReader, HeaderWriter}

/**
  * Interoperability abstractions to handle HTTP Request and Response messages.
  */
object HttpMessage {

  /**
    * Adapter for reading information from a HTTP Request message.
    */
  trait Request extends HeaderReader {

    /**
      * Request URL.
      */
    def url: String

    /**
      * Full request path. Does not include the query.
      */
    def path: String

    /**
      * HTTP Method.
      */
    def method: String

    /**
      * Host that will be receiving the request.
      */
    def host: String

    /**
      * Port number at which the request was addressed.
      */
    def port: Int
  }

  /**
    * Adapter for reading information from a HTTP response message.
    */
  trait Response {

    /**
      * Status code on the response message.
      */
    def statusCode: Int
  }

  /**
    * A HTTP message builder on which header values can be written and a complete HTTP message can be build from.
    * Implementations will typically wrap a HTTP message model from an instrumented framework and either accumulate
    * all header writes until a call to to .build() is made and a new HTTP message is constructed merging the previous
    * and accumulated headers (on immutable HTTP models) or directly write the headers on the underlying HTTP message
    * (on mutable HTTP models).
    */
  trait Builder[Message] extends HeaderWriter {

    /**
      * Returns a new HTTP message containing all headers that have been written to the builder.
      */
    def build(): Message
  }

  /**
    * Builder for HTTP Request messages
    */
  trait RequestBuilder[Message] extends Request with Builder[Message]

  /**
    * Builder for HTTP Response messages.
    */
  trait ResponseBuilder[Message] extends Response with Builder[Message]

}
