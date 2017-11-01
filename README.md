# Play Framework Integration <img align="right" src="https://rawgit.com/kamon-io/Kamon/master/kamon-logo.svg" height="150px" style="padding-left: 20px"/>
[![Build Status](https://travis-ci.org/kamon-io/kamon-play.svg?branch=kamon-1.0)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kamon-io/Kamon?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-play-2.6_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-play-2.6_2.12)

The `kamon-play` module ships with bytecode instrumentation that brings automatic traces and spans management to your Play! applications. The details on what those features are about can be found
in the [base functionality] documentation section. Here we will dig into the specific aspects of bringing support for them when using
Play!.

The <b>kamon-play</b> module requires you to start your application using the AspectJ Weaver Agent. Kamon will warn you
at startup if you failed to do so.

Since Kamon 1.0.0 we support both Play Framework 2.4, 2.5 and 2.6, but bringing support for Play!. Please make sure you add either <b>kamon-play-24</b>, <b>kamon-play-25</b> or <b>kamon-play-26</b> to your project's classpath.

### Getting Started

Kamon Play is currently available for Scala 2.10, 2.11 and 2.12.

Supported releases and dependencies are shown below.

| kamon      | status | jdk  | scala            
|:----------:|:------:|:----:|------------------
|  1.0.0-RC1 |   RC   | 1.8+ | 2.10, 2.11, 2.12

To get started with SBT, simply add the following to your `build.sbt` or `pom.xml`
file:

```scala
resolvers += Resolver.bintrayRepo("kamon-io", "snapshots")
libraryDependencies += "io.kamon" %% "kamon-play-[play-version]" % "1.0.0-RC1"
```

```xml
<dependency>
    <groupId>io.kamon</groupId>
    <artifactId>kamon-play-[play-version]_2.12</artifactId>
    <version>1.0.0-RC1-e2eaf5e8bc4d51145e2d7f542e43e1023f9fda95</version>
</dependency>
```


[base functionality]: http://kamon.io/integrations/web-and-http-toolkits/base-functionality/
