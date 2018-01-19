# Kamon<img align="right" src="https://rawgit.com/kamon-io/Kamon/master/kamon-logo.svg" height="150px" style="padding-left: 20px"/>
[![Build Status](https://travis-ci.org/kamon-io/Kamon.svg?branch=master)](https://travis-ci.org/kamon-io/Kamon)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kamon-io/Kamon?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-core_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-core_2.12)

Kamon is a set of tools for monitoring applications running on the JVM.

### Getting Started

Kamon is currently available for Scala 2.10, 2.11 and 2.12.

Supported releases and dependencies are shown below.

| kamon  | status | jdk  | scala            
|:------:|:------:|:----:|------------------
|  1.0.0 | stable | 1.7+, 1.8+ | 2.10, 2.11, 2.12

To get started with SBT, simply add the following to your `build.sbt` or `pom.xml`
file:

```scala
libraryDependencies += "io.kamon" %% "kamon-core" % "1.0.0"
```

```xml
<dependency>
    <groupId>io.kamon</groupId>
    <artifactId>kamon-core_2.12</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Documentation

Kamon information and documentation is available on the
[website](http://kamon.io).

### Modules ###

We started migrating modules to Kamon `1.0.0` based on the usage data we have, community demand and time availability, but
not all have been upgraded just yet. 

Now, the lucky modules currently available are:
  - [Core](https://github.com/kamon-io/kamon) includes all metrics, tracing and context management APIs.
  - [Akka](https://github.com/kamon-io/kamon-akka) for actor metrics and tracing inside a single JVM.
  - [Akka Remote](https://github.com/kamon-io/kamon-akka-remote) has now serialization and remoting metrics and is able
    to trace messages across remote actor systems.
  - [Akka HTTP](https://github.com/kamon-io/kamon-akka-http) with client and service side tracing and HTTP server metrics.
  - [Futures](https://github.com/kamon-io/kamon-futures) bring automatic context propagation for Scala, Finagle and
    Scalaz futures.
  - [Executors](https://github.com/kamon-io/kamon-executors) collects executor service metrics.
  - [Play Framework](https://github.com/kamon-io/kamon-futures) with client and server side tracing.
  - [JDBC](https://github.com/kamon-io/kamon-jdbc) gives you metrics and tracing for JDBC statements execution and
    Hikari pool metrics.
  - [Logback](https://github.com/kamon-io/kamon-logback) comes with utilities for adding trace IDs to your logs and
    instrumentation to keep context when using async appenders.
  - [System Metrics](https://github.com/kamon-io/kamon-system-metrics) gathers host, process and JVM metrics.

### Backends ###

  - [Promethus](https://github.com/kamon-io/kamon-prometheus) exposes a scrape endpoint with all available metrics.
  - [Zipkin](https://github.com/kamon-io/kamon-zipkin) for reporting trace data.
  - [Jaeger](https://github.com/kamon-io/kamon-jaeger) reports tracing data as well.
  - [Kamino](https://github.com/kamino-apm/kamino-reporter) reports metrics and tracing data to [Kamino][8]


## License

This software is licensed under the Apache 2 license, quoted below.

Copyright © 2013-2018 the kamon project <http://kamon.io>

Licensed under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License. You may obtain a copy of
the License at

    [http://www.apache.org/licenses/LICENSE-2.0]

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under
the License.

[1]: https://github.com/dpsoft
[2]: https://github.com/ivantopo
[3]: /documentation/1.x/recipes/migrating-from-kamon-0.6.x/
[4]: https://research.google.com/pubs/pub36356.html
[5]: https://twitter.github.io/finagle/guide/Contexts.html
[6]: https://grpc.io/grpc-java/javadoc/io/grpc/Context.html
[7]: https://gitter.im/kamon-io/Kamon
[8]: https://kamino.io/
