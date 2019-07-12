# Futures Instrumentation<img align="right" src="https://rawgit.com/kamon-io/Kamon/master/kamon-logo.svg" height="150px" style="padding-left: 20px"/>

[![Build Status](https://travis-ci.org/kamon-io/kamon-scala.svg?branch=master)](https://travis-ci.org/kamon-io/kamon-scala)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kamon-io/Kamon?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-scala-future_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-scala-future_2.12)

### Getting Started

The Futures instrumentation is currently available for Scala 2.10, 2.11 and 2.12.

Supported releases and dependencies are shown below.

| kamon-futures  | status | jdk  | scala
|:------:|:------:|:----:|------------------
|  1.0.0 | stable | 1.7+, 1.8+ | 2.10, 2.11, 2.12

To get started with SBT, simply add the following to your `build.sbt`
file:

```scala
resolvers += Resolver.bintrayRepo("kamon-io", "snapshots")

libraryDependencies += "io.kamon" %% "kamon-scala-future" % "1.0.0"
libraryDependencies += "io.kamon" %% "kamon-scalaz-future" % "1.0.0"
libraryDependencies += "io.kamon" %% "kamon-twitter-future" % "1.0.0"
```


## Automatic TraceContext Propagation with Futures

The `kamon-futures` module provides bytecode instrumentation for Scala, Scalaz and Twitter Futures that automatically
propagates the `Context` across the asynchronous operations that might be scheduled for a given `Future`.

All modules in the <b>kamon-futures</b> project module require you to start your application using the AspectJ Weaver
Agent.

### Instrumenting Future's Body and Callbacks ###

In the following piece of code, the body of the future will be executed asynchronously on some other thread provided by
the ExecutionContext available in implicit scope, but Kamon will capture the `TraceContext` available when the future
was created and make it available while executing the future's body.

```scala
  val context = contextWithLocal("in-future-transformations")
        val baggageAfterTransformations = Kamon.withContext(context) {
            Future("Hello Kamon!")
              // The active span is expected to be available during all intermediate processing.
              .map(_.length)
              .flatMap(len => Future(len.toString))
              .map(_ => Kamon.currentContext().get(StringKey))
          }
```

Also, when you transform a future by using map/flatMap/filter and friends or you directly register a
onComplete/onSuccess/onFailure callback on a future, Kamon will capture the `Context` available when transforming
the future and make it available when executing the given callback. The code snippet above would print the same
`Context` that was available when creating the future, during it's body execution and during the execution of all
the asynchronous operations scheduled on it.
