# Kamon Jaeger <img align="right" src="https://rawgit.com/kamon-io/Kamon/master/kamon-logo.svg" height="150px" style="padding-left: 20px"/> 
[![Build Status](https://travis-ci.org/kamon-io/kamon-jaeger.svg?branch=master)](https://travis-ci.org/kamon-io/kamon-jaeger)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kamon-io/Kamon?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-jaeger_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-jaeger_2.12)


### Getting Started

Kamon Jaeger is currently available for Scala 2.10, 2.11 and 2.12.

Supported releases and dependencies are shown below.

| kamon  | status | jdk  | scala            
|:------:|:------:|:----:|------------------
|  1.0.0 | experimental | 1.8+ | 2.11, 2.12

To get started with SBT, simply add the following to your `build.sbt` or `pom.xml`
file:

```scala
libraryDependencies += "io.kamon" %% "kamon-jaeger" % "1.0.2"
```

```xml
<dependency>
    <groupId>io.kamon</groupId>
    <artifactId>kamon-jaeger_2.12</artifactId>
    <version>1.0.2</version>
</dependency>
```


#### Custom environment tags
Kamon allows you to provide custom environment tags to all your metrics by configuring `kamon.environment.tags` in your `application.conf`, e.g.
```
kamon.environment.tags {
  custom.id = "test1"
  env = staging
}
```
In order to include these tags in your Prometheus metrics as well, you need to activate this feature for the `JaegerReporter` by setting
```
kamon.jaeger.include-environment-tags = yes
```
in your `application.conf`.