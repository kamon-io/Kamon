---
title: Kamon | Datadog | Documentation
layout: default
---

Reporting Metrics to Datadog
===========================
<hr>

[Datadog](http://www.datadoghq.com/) Datadog is a monitoring service for IT, Operations and Development teams who write
and run applications at scale, and want to turn the massive amounts of data produced by their apps,
tools and services into actionable insight.

Installation
------------

To use the Datadog module just add the `kamon-datadog` dependency to your project and start your application using the
Aspectj Weaver agent. Please refer to our [get started](/get-started) page for more info on how to add dependencies to
your project and starting your application with the AspectJ Weaver.


Configuration
-------------

First, include the Kamon(Datadog) extension under the `akka.extensions` key of your configuration files as shown here:

```scala
akka {
  extensions = ["kamon.statsd.Datadog"]
}
```

Then, tune the configuration settings according to your needs. Here is the `reference.conf` that ships with kamon-datadog
which includes a brief explanation of each setting:

```
kamon {
  datadog {
    # Hostname and port in which your StatsD is running. Remember that Datadog packets are sent using UDP and
    # setting unreachable hosts and/or not open ports wont be warned by the Kamon, your data wont go anywhere.
    hostname = "127.0.0.1"
    port = 8125

    # Interval between metrics data flushes to Datadog. It's value must be equal or greater than the
    # kamon.metrics.tick-interval setting.
    flush-interval = 1 second

    # Max packet size for UDP metrics data sent to Datadog.
    max-packet-size = 1024 bytes

    # Subscription patterns used to select which metrics will be pushed to Datadog. Note that first, metrics
    # collection for your desired entities must be activated under the kamon.metrics.filters settings.
    includes {
      actor = [ "*" ]
      trace = [ "*" ]
    }

    simple-metric-key-generator {
      # Application prefix for all metrics pushed to Datadog. The default namespacing scheme for metrics follows
      # this pattern:
      #    application.host.entity.entity-name.metric-name
      application = "kamon"
    }
  }
}
```


Integration Notes
-----------------

* Contrary to many Datadog client implementations, we don't flush the metrics data as soon as the measurements are taken
  but instead, all metrics data is buffered by the `Kamon(Datadog)` extension and flushed periodically using the
  configured `kamon.statsd.flush-interval` and `kamon.statsd.max-packet-size` settings.
* Currently only Actor and Trace metrics are being sent to Datadog.
* All timing measurements are sent in nanoseconds, make sure you correctly set the scale when plotting or using the
  metrics data.
* It is advisable to experiment with the `kamon.statsd.flush-interval` and `kamon.statsd.max-packet-size` settings to
  find the right balance between network bandwidth utilization and granularity on your metrics data.



Visualization and Fun
---------------------

