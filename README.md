Kamon akka-http
--------------------
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kamon-io/Kamon?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://api.travis-ci.org/kamon-io/kamon-akka-http.png)](https://travis-ci.org/kamon-io/kamon-akka-http/builds)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-akka-http_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-akka-http_2.12)

--------------------

*Kamon Akka Http* module provides bytecode instrumentation to gather metrics and perform automatic TraceContext propagation on your behalf, both on the client and server side.

##This module currently supports:
* [Traces] in the server side and allow configure the ```X-Trace-Context``` in order to pass into the current request.
* [HttpServerMetrics] that gather metrics with status code and categories + request-active and connection-open (as your snippet)
* [Segments] for client-side requests

### Getting Started

Kamon akka-http module is currently available for Scala 2.11 and 2.12.

Supported releases and dependencies are shown below.

| kamon-akka-http  | status | jdk  | scala            | akka   |
|:------:|:------:|:----:|------------------|:------:|
|  0.6.7 | stable | 1.8+ |  2.11, 2.12  | 2.4.x |

To get started with SBT, simply add the following to your `build.sbt`
file:

```scala
libraryDependencies += "io.kamon" %% "kamon-akka-http" % "0.6.7"
```

The following is an example of a configuration to provide metrics to a server, such as Statsd.

```
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
}
```

You can also provide some settings to this module. Currently the default config is:

```
kamon {
  akka-http {

    # Header name used when propagating the `TraceContext.token` value across applications.
    trace-token-header-name = "X-Trace-Token"

    # When set to true, Kamon will automatically set and propogate the `TraceContext.token` value.
    automatic-trace-token-propagation = true

    # Fully qualified name of the implementation of kamon.akka.http.AkkaHttpNameGenerator that will be used for assigning names
    # to traces and client http segments.
    name-generator = kamon.akka.http.DefaultNameGenerator

    client {
      # Strategy used for automatic trace segment generation when issue requests with akka-http-client. The possible values
      # are: request-level and host-level (Not implemented yet!).
      instrumentation-level = request-level
    }
  }
}
```

A better explanation is provided in the kamon-akka-http's [reference.conf].

### Run

*kamon-akka-http* module require you to start your application using the AspectJ Weaver Agent. Kamon will warn you at startup if you failed to do so.

To achieve it quickly you can use the [sbt-aspectj-runner] plugin.

### Enjoy!

## Examples

Coming soon!

## Naming HTTP Client Segments
By default, the name generator bundled with the *kamon-akka-http* module will use the Host header available in the request to assign a name to the automatically generated segment. Currently, the only way to override that name would be to provide your own implementation of `kamon.akka.http.AkkaHttpNameGenerator` which is used to assign names both on the server and client sides of our instrumentation.

## Backlog
* Connection-Level Client-Side API
* Host-Level Client-Side API support
* Take some ideas from [spray-kamon-metrics]

[spray-kamon-metrics]: http://engineering.monsanto.com/2015/09/24/better-spray-metrics-with-kamon/
[Traces]: https://github.com/kamon-io/kamon-akka-http/blob/master/src/main/scala/kamon/akka/http/instrumentation/FlowWrapper.scala#L36-L49
[HttpServerMetrics]:https://github.com/kamon-io/Kamon/blob/master/kamon-core/src/main/scala/kamon/util/http/HttpServerMetrics.scala#L27
[Segments]:https://github.com/kamon-io/kamon-akka-http/blob/master/src/main/scala/kamon/akka/http/instrumentation/ClientRequestInstrumentation.scala#L32-L45
[sbt-aspectj-runner]: https://github.com/kamon-io/sbt-aspectj-runner
[reference.conf]: https://github.com/kamon-io/kamon-akka-http/blob/master/src/main/resources/reference.conf
