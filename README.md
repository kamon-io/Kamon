# Prometheus Integration <img align="right" src="https://rawgit.com/kamon-io/Kamon/master/kamon-logo.svg" height="150px" style="padding-left: 20px"/>
[![Build Status](https://travis-ci.org/kamon-io/kamon-prometheus.svg?branch=master)](https://travis-ci.org/kamon-io/kamon-prometheus)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kamon-io/Kamon?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-prometheus_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-prometheus_2.11)

### Getting Started

Kamon Prometheus is currently available for Scala 2.10, 2.11 and 2.12.

Supported releases and dependencies are shown below.

| kamon      | status | jdk  | scala            
|:----------:|:------:|:----:|------------------
|  1.0.0-RC1 |   RC   | 1.8+ | 2.10, 2.11, 2.12

To get started with SBT, simply add the following to your `build.sbt` or `pom.xml`
file:

```scala
resolvers += Resolver.bintrayRepo("kamon-io", "snapshots")
libraryDependencies += "io.kamon" %% "kamon-prometheus" % "1.0.0-RC1-e2eaf5e8bc4d51145e2d7f542e43e1023f9fda95"
```

```xml
<dependency>
    <groupId>io.kamon</groupId>
    <artifactId>kamon-prometheus_2.12</artifactId>
    <version>1.0.0-RC1-e2eaf5e8bc4d51145e2d7f542e43e1023f9fda95</version>
</dependency>
```
