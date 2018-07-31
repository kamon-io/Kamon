# Play Framework Integration <img align="right" src="https://rawgit.com/kamon-io/Kamon/master/kamon-logo.svg" height="150px" style="padding-left: 20px"/>
![Build Status](https://travis-ci.org/kamon-io/kamon-play.svg?branch=kamon-1.0)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kamon-io/Kamon?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-play-2.6_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-play-2.6_2.12)

The `kamon-play` module ships with bytecode instrumentation that brings distributed tracing, metrics and automatic context propagation to Play Framework applications. 

NOTE: The <b>kamon-play</b> module requires you to start your application using the AspectJ Weaver Agent.

Since Kamon 1.0.0 we support Play Framework 2.4, 2.5 and 2.6!. Please make sure you add either <b>kamon-play-2.4</b>, <b>kamon-play-2.5</b> or <b>kamon-play-2.6</b> to your project's classpath.

### Getting Started

Kamon Play is currently available for Scala 2.10, 2.11 and 2.12.

Supported releases and dependencies are shown below.

| kamon-play-2.4  | status | jdk        | scala            
|:---------------:|:------:|:----------:|------------------
|  1.1.0          | stable | 1.7+, 1.8+ | 2.10, 2.11

| kamon-play-2.5  | status | jdk        | scala   
|:---------------:|:------:|:----------:|------------------
|  1.1.0         | stable | 1.7+, 1.8+ | 2.11

| kamon-play-2.6  | status | jdk        | scala   
|:---------------:|:------:|:----------:|------------------
|  1.1.0         | stable | 1.8+       | 2.12  


For Play Framework 2.6, just add the dependency bellow and ensure your application is [running with the AspectJ Weaver][1]

```scala
libraryDependencies += "io.kamon" %% "kamon-play-2.6" % "1.1.0"
```

[1]: http://kamon.io/documentation/1.x/recipes/adding-the-aspectj-weaver/
