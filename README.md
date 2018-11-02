# Akka Integration<img align="right" src="https://rawgit.com/kamon-io/Kamon/master/kamon-logo.svg" height="150px" style="padding-left: 20px"/>

[![Build Status](https://travis-ci.org/kamon-io/kamon-akka.svg?branch=master)](https://travis-ci.org/kamon-io/kamon-akka)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kamon-io/Kamon?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-akka-2.5_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-akka-2.5_2.12)

Kamon's integration with Akka comes in the form of two modules: `kamon-akka` and `kamon-akka-remote` that bring bytecode
instrumentation to gather metrics and perform automatic `Context` propagation on your behalf.

The <b>kamon-akka</b> module require you to start your application using the AspectJ Weaver Agent. Kamon will warn you at startup if you failed to do so.

### Getting Started

Kamon akka module is currently available for Scala 2.10, 2.11 and 2.12.

Supported releases and dependencies are shown below.

| Artifact              | status | jdk        | scala            | akka   |
|:---------------------:|:------:|:----------:|------------------|:------:|
|  kamon-akka-2.3 1.0.1 | stable | 1.7+, 1.8+ | 2.10, 2.11       | 2.3.x  |
|  kamon-akka-2.4 1.1.0 | stable | 1.7+, 1.8+ | 2.11, 2.12       | 2.4.x  |
|  kamon-akka-2.5 1.1.0 | stable | 1.8+       | 2.11, 2.12       | 2.5.x  |

To get started with SBT, simply add the following to your `build.sbt` or `pom.xml`
file:

```scala
libraryDependencies += "io.kamon" %% "kamon-akka-2.5" % "1.1.0"
```

```xml
<dependency>
    <groupId>io.kamon</groupId>
    <artifactId>kamon-akka-2.5_2.12</artifactId>
    <version>1.1.0</version>
</dependency>
```

A full description of the capabilities of this module can be found in [the official documentation][1].


[1]: http://kamon.io/documentation/1.x/instrumentation/akka/
