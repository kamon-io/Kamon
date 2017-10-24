InfluxDB Integration    ![Build Status](https://travis-ci.org/kamon-io/kamon-influxdb.svg?branch=master)
==========================

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kamon-io/Kamon?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-influxdb_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-influxdb_2.11)

Reporting Metrics to InfluxDB
=============================

[InfluxDB] is a high performance open source database for handling time series
data. It receives metrics over HTTP or UDP using its own line protocol. It's
a high availability and high performance database and provides an excellent
backend for monitoring.

Installation
------------

Add the `kamon-influxdb` dependency to your project and ensure that it is in
your classpath at runtime. Kamon's module loader will detect that
the InfluxDB module is in the classpath and automatically starts it.

### Getting Started

Kamon influxdb module is currently available for Scala 2.10, 2.11 and 2.12.

Supported releases and dependencies are shown below.

| kamon-influxdb  | status | jdk  | scala            |
|:------:|:------:|:----:|------------------|
|  0.6.7 | stable | 1.8+ |  2.10, 2.11, 2.12  |

To get started with SBT, simply add the following to your `build.sbt`
file:

```scala
libraryDependencies += "io.kamon" %% "kamon-influxdb" % "0.6.7"
```


Configuration
-------------

At the very basic level, you will certainly want to use the
`kamon.influxdb.hostname` and `kamon.influxdb.port` configuration keys to
ensure your data is being sent to wherever your InfluxDB database is running.
Additionally to that, you can configure the metric categories to which this
module will subscribe using the `kamon.influxdb.subscriptions` key. By default,
the following subscriptions are included:

```typesafeconfig
kamon.influxdb {
  subscriptions {
    histogram       = [ "**" ]
    min-max-counter = [ "**" ]
    gauge           = [ "**" ]
    counter         = [ "**" ]
    trace           = [ "**" ]
    trace-segment   = [ "**" ]
    akka-actor      = [ "**" ]
    akka-dispatcher = [ "**" ]
    akka-router     = [ "**" ]
    system-metric   = [ "**" ]
    http-server     = [ "**" ]
  }
}
```

By default, the module sends data to InfluxDB through its HTTP(S) interface. While
the HTTP instance has a richer API compared to UDP, it is much slower to send
data through it. It is possible to optimize the sends by setting a higher
`kamon.influxdb.max-packet-size`, the default is 16 kB.

Using the HTTP protocol it's possible to define the database where the data is
stored. You can change the value by modifying the `kamon.influxdb.database`
configuration. With UDP this setting has to be done in the InfluxDB
configuration. To switch the protocol, change the `kamon.influxdb.protocol` to
`udp`.

If the server requires authentication, define the write user and password by
setting the values for `kamon.influxdb.authentication.user` and
`kamon.influxdb.authentication.password`. The retention policy can be defined
by changing the `kamon.influxdb.authentication.retention-policy`. When using
UDP, change these values from InfluxDB configuration.

### Metric Key Generators ###

The `kamon.influxdb.application-name` affects to the measurement names stored
in InfluxDB. By default, the value is `kamon`. The data is stored into two
measurements in the selected database: `kamon-counters` and
`kamon-timers`, where `kamon` is replaced by the chosen `application-name`. The
measurements are sent with the following tags:

* __category__: The entity's category.
* __entity__: The entity's name.
* __metric__: The metric name assigned in the entity recorder.
* __hostname__: Uses the local host name or `kamon.influxdb.hostname-override`,
  if given.

The counter metrics store their current value to the `value` field. For
histograms, the following fields are stored:

* __lower__: The lowest value.
* __mean__: The average value.
* __upper__: The highest value.

Additionally, it's possible to define the percentiles to be stored to InfluxDB
for every tick. To change these, modify the `kamon.influxdb.percentiles`
configuration. Default percentiles are `[50.0, 70.0, 90.0, 95.0, 99.0, 99.9]`.
For example, the configuration could be `[50.0, 70.5, 90.0]`, so the following
fields would be stored:

* __p50__: 50th percentile
* __p70.5__: 70.5th percentile
* __p90__: 90th percentile

Using the InfluxDB data
-----------------------

InfluxDB is a fast, powerful and reliable database. The stored measurements can
be queried using an SQL-like language. There are many good interfaces for
visualizing the data, including [Grafana] and [Chronograf]. InfluxDB can handle
huge amounts of data and scales easily with your project. It is very fast even
with large datasets, resulting fast and beautiful dashboards for monitoring.

[InfluxDB]: https://influxdata.com/time-series-platform/influxdb/
[Chronograf]: https://influxdata.com/time-series-platform/chronograf/
[Grafana]: http://grafana.org
