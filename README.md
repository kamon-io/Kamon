# Kamon APM Reporter<img align="right" src="https://rawgit.com/kamon-io/Kamon/master/kamon-logo.svg" height="150px" style="padding-left: 20px"/>

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kamon-io/Kamon?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-apm-reporter_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-apm-reporter_2.12)

The `kamon-apm-reporter` module allows you to send metrics and tracing data collected by Kamon to [Kamon APM][1].

### Latest Version

| kamon-apm | status | jdk  | scala            |
|:---------:|:------:|:----:|------------------|
|  2.0.3    | Stable | 1.8+ | 2.11, 2.12, 2.13 |



To get started with SBT add the following to your `build.sbt` file:

```scala
libraryDependencies += "io.kamon" %% "kamon-apm-reporter" % "2.0.3"
```


You can find more info on [kamon.io][2].

[1]: https://kamon.io/apm/
[2]: https://kamon.io/docs/latest/reporters/apm/
