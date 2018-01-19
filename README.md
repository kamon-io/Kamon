Kamon akka-http
--------------------
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kamon-io/Kamon?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://api.travis-ci.org/kamon-io/kamon-akka-http.png)](https://travis-ci.org/kamon-io/kamon-akka-http/builds)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-akka-http-2.5_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-akka-http-2.5_2.12)

--------------------

*Kamon Akka Http* module provides bytecode instrumentation to gather metrics and perform automatic `Context` propagation on your behalf, both on the client and server side.

### Getting Started

Kamon akka-http module is currently available for Scala 2.11 and 2.12.

Supported releases and dependencies are shown below.

| kamon-akka-http-2.4  | status | jdk  | scala            | akka   |
|:------:|:------:|:----:|------------------|:------:|
|  1.0.0 | stable | 1.8+ |  2.11, 2.12  | 2.4.x |

| kamon-akka-http-2.5  | status | jdk  | scala            | akka   |
|:------:|:------:|:----:|------------------|:------:|
|  1.0.0 | stable | 1.8+ |  2.11, 2.12  | 2.5.x |


To get started with SBT, simply add the following to your `build.sbt` or `pom.xml`
file:

```scala
libraryDependencies += "io.kamon" %% "kamon-akka-http-2.5" % "1.0.0"

```xml
<dependency>
    <groupId>io.kamon</groupId>
    <artifactId>kamon-akka-http-2.5_2.12</artifactId>
    <version>1.0.0</version>
</dependency>
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
