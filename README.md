Kamon akka-http
--------------------
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kamon-io/Kamon?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://api.travis-ci.org/kamon-io/kamon-akka-http.png)](https://travis-ci.org/kamon-io/kamon-akka-http/builds)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-akka-http_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-akka-http_2.12)

--------------------

*Kamon Akka Http* module provides bytecode instrumentation to gather metrics and perform automatic `Context` propagation on your behalf, both on the client and server side.

### Adding the Module

Supported releases and dependencies are shown below.

| kamon-akka-http  | status | jdk  | scala            | akka   |
|:------:|:------:|:----:|------------------|:------:|
|  2.0.2 | stable | 1.8+ |  2.11, 2.12, 2.13  | 2.5.x |


| kamon-akka-http-2.4  | status | jdk  | scala            | akka   |
|:------:|:------:|:----:|------------------|:------:|
|  1.1.2 | stable | 1.8+ |  2.11, 2.12  | 2.4.x |


To get started with SBT add the following to your `build.sbt` or `pom.xml`
file:

```scala
libraryDependencies += "io.kamon" %% "kamon-akka-http" % "2.0.2"
```

```xml
<dependency>
    <groupId>io.kamon</groupId>
    <artifactId>kamon-akka-http_2.12</artifactId>
    <version>2.0.2</version>
</dependency>
```

### Run

The `kamon-akka-http` module requires you to start your application using the Kanela Agent. You can achieve that quickly with the [sbt-kanela-runner] plugin or take a look at the [documentation] for other options.

### Enjoy!

Feel free to ask anything you might need in our Gitter channel or in the mailing list.

[sbt-kanela-runner]: https://github.com/kamon-io/sbt-kanela-runner
[reference.conf]: https://github.com/kamon-io/kamon-akka-http/blob/master/src/main/resources/reference.conf
[documentation]: https://kamon.io/docs/latest/guides/installation/setting-up-the-agent/
