WIP: Kamon Akka-Http
--------------------
A temporary implementation of the Kamon Akka-Http(based on @jypma http://pastebin.com/DHVb54iK and kamon-spray module) module.

This WIP currently supports: 
* [Traces] in the server side and allow configure the ```X-Trace-Context``` in order to  pass into the current request request.]
* [HttpServerMetrics] that gather metrics with status code and categories + request-active and connection-open (as your snippet)
* [Segments] for client-side requests 

*Backlog*
* Connection-Level Client-Side API
* Host-Level Client-Side API support
* Tests

[Traces]: https://github.com/kamon-io/kamon-akka-http/blob/master/src/main/scala/kamon/akka/http/instrumentation/FlowWrapper.scala#L36-L49
[HttpServerMetrics]:https://github.com/kamon-io/Kamon/blob/master/kamon-core/src/main/scala/kamon/util/http/HttpServerMetrics.scala#L27
[Segments]:https://github.com/kamon-io/kamon-akka-http/blob/master/src/main/scala/kamon/akka/http/instrumentation/ClientRequestInstrumentation.scala#L32-L45


*Configuration*
```scala
kamon {
  metric {
    filters {
      trace.includes = [ "**" ]
    }
  }
  
  subscriptions {
      trace                = [ "**" ]
      trace-segment        = [ "**" ]
      akka-http-server     = [ "**" ]
    }
```
