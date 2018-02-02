Kamon akka-http
--------------------
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kamon-io/Kamon?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://api.travis-ci.org/kamon-io/kamon-akka-http.png)](https://travis-ci.org/kamon-io/kamon-akka-http/builds)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-akka-http-2.5_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-akka-http-2.5_2.12)

--------------------

*Kamon Akka Http* module provides bytecode instrumentation to gather metrics and perform automatic `Context` propagation on your behalf, both on the client and server side.

### Adding the Module

Supported releases and dependencies are shown below.

| kamon-akka-http-2.4  | status | jdk  | scala            | akka   |
|:------:|:------:|:----:|------------------|:------:|
|  1.0.1 | stable | 1.8+ |  2.11, 2.12  | 2.4.x |

| kamon-akka-http-2.5  | status | jdk  | scala            | akka   |
|:------:|:------:|:----:|------------------|:------:|
|  1.0.1 | stable | 1.8+ |  2.11, 2.12  | 2.5.x |


To get started with SBT add the following to your `build.sbt` or `pom.xml`
file:

```scala
libraryDependencies += "io.kamon" %% "kamon-akka-http-2.5" % "1.0.1"
```

```xml
<dependency>
    <groupId>io.kamon</groupId>
    <artifactId>kamon-akka-http-2.5_2.12</artifactId>
    <version>1.0.1</version>
</dependency>
```

### Run

The `kamon-akka-http` module requires you to start your application using the AspectJ Weaver Agent. You can achieve that quickly with the [sbt-aspectj-runner] plugin or take a look at the [documentation] for other options.

### Enjoy!

Feel free to ask anything you might need in our Gitter channel or in the mailing list.

[spray-kamon-metrics]: http://engineering.monsanto.com/2015/09/24/better-spray-metrics-with-kamon/
[Traces]: https://github.com/kamon-io/kamon-akka-http/blob/master/src/main/scala/kamon/akka/http/instrumentation/FlowWrapper.scala#L36-L49
[HttpServerMetrics]:https://github.com/kamon-io/Kamon/blob/master/kamon-core/src/main/scala/kamon/util/http/HttpServerMetrics.scala#L27
[Segments]:https://github.com/kamon-io/kamon-akka-http/blob/master/src/main/scala/kamon/akka/http/instrumentation/ClientRequestInstrumentation.scala#L32-L45
[sbt-aspectj-runner]: https://github.com/kamon-io/sbt-aspectj-runner
[reference.conf]: https://github.com/kamon-io/kamon-akka-http/blob/master/src/main/resources/reference.conf
[documentation]: http://kamon.io/documentation/1.x/recipes/adding-the-aspectj-weaver/
