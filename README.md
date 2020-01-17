# Kamon Zipkin Reporter <img align="right" src="https://rawgit.com/kamon-io/Kamon/master/kamon-logo.svg" height="150px" style="padding-left: 20px"/>

[![Build Status](https://travis-ci.org/kamon-io/kamon-zipkin.svg?branch=master)](https://travis-ci.org/kamon-io/kamon-zipkin)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kamon-io/Kamon?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-zipkin_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-zipkin_2.12)

The kamon-zipkin module allows you to send tracing data collected by Kamon to Zipkin.

### Getting Started

Currently available for Scala 2.11, 2.12 and 2.13.

Supported releases and dependencies are shown below.

| kamon-zipkin | status | jdk  | scala            |
|:------------:|:------:|:----:|------------------|
|  2.0.1       | stable | 1.8+ | 2.11, 2.12, 2.13 |


To get started with SBT, simply add the following to your `build.sbt` file:

```scala
libraryDependencies += "io.kamon" %% "kamon-zipkin" % "2.0.1"
```

You can find more info on [kamon.io](https://kamon.io) and in our [Elementary Akka Setup Guide][1]

[1]: https://kamon.io/docs/latest/guides/frameworks/elementary-akka-setup/
