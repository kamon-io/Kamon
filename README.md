InfluxDB Integration    ![Build Status](https://travis-ci.org/kamon-io/kamon-influxdb.svg?branch=master)
==========================

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kamon-io/Kamon?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-influxdb_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-influxdb_2.11)

Reporting Metrics to InfluxDB
=============================

[InfluxDB][1] is a high performance open source database for handling time series data. It receives metrics over HTTP or UDP
using its own line protocol. It's a high availability and high performance database and provides an excellent backend
for monitoring.

The `kamon-influxdb` module only supports sending data to InfluxDB over HTTP.

Installation
------------

Add the `kamon-influxdb` dependency to your project and ensure that it is in
your classpath at runtime. Kamon's module loader will detect that
the InfluxDB module is in the classpath and automatically starts it.

### Getting Started

The `kamon-influxdb` module is currently available for Scala 2.10, 2.11 and 2.12.

Supported releases and dependencies are shown below.

| kamon-influxdb  | status | jdk  | scala              |
|:---------------:|:------:|:----:|--------------------|
|  1.0.1          | stable | 1.8+ |  2.10, 2.11, 2.12  |

To get started with SBT, simply add the following to your `build.sbt` or `pom.xml` files:

```scala
libraryDependencies += "io.kamon" %% "kamon-influxdb" % "1.0.1"
```

```xml
<dependency>
    <groupId>io.kamon</groupId>
    <artifactId>kamon-influxdb_2.12</artifactId>
    <version>1.0.1</version>
</dependency>
```


A full description of the capabilities of this module can be found in [the official documentation][2].


[1]: https://www.influxdata.com/
[2]: http://kamon.io/documentation/1.x/reporters/influxdb/
