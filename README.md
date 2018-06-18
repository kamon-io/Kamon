# Kamino Reporter for Kamon<img align="right" src="https://rawgit.com/kamon-io/Kamon/master/kamon-logo.svg" height="150px" style="padding-left: 20px"/>

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kamon-io/Kamon?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamino-reporter_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamino-reporter_2.12)

The `kamino-reporter` module allows you to send metrics and tracing data collected by Kamon to [Kamino][1].

### Getting Started

Currently available for Scala 2.11 and 2.12.

Supported releases and dependencies are shown below.

| kamino-reporter | status | jdk  | scala            |
|:---------------:|:------:|:----:|------------------|
|    1.1.0    |   RC   | 1.8+ |    2.11, 2.12    |



To get started with SBT, simply add the following to your `build.sbt` file:

```scala
libraryDependencies += "io.kamon" %% "kamino-reporter" % "1.1.0"
```


You can find more info on [kamon.io][2].

[1]: https://kamino.io/
[2]: http://kamon.io/documentation/1.x/reporters/kamino/
