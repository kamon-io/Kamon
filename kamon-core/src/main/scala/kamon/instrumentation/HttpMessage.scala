package kamon.instrumentation

import kamon.context.HttpPropagation.{HeaderReader, HeaderWriter}

/**
  * Base abstractions over HTTP messages.
  */
object HttpMessage {

  /**
    * Wrapper for HTTP Request messages.
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
  }

  /**
    * Wrapper for HTTP response messages.
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
      * Returns a version a version of the HTTP message container all headers that have been written to the builder.
      */
    def build(): Message
  }

  /**
    * Builder for HTTP Request messages.
    */
  trait RequestBuilder[Message] extends Request with Builder[Message]

  /**
    * Builder for HTTP Response messages.
    */
  trait ResponseBuilder[Message] extends Response with Builder[Message]
}
