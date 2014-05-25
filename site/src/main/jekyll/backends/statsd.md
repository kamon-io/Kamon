---
title: Kamon | StatsD | Documentation
layout: documentation
---

Reporting Metrics to StatsD
===========================
<hr>

[StatsD](https://github.com/etsy/statsd/) is a simple network daemon that continuously receives metrics over UDP and
periodically sends aggregate metrics to upstream services like (but not limited to) Graphite. Because it uses UDP,
sending metrics data to StatsD is very fast with little to no overhead.


Installation
------------

To use the StatsD module just add the `kamon-statsd` dependency to your project and start your application using the
Aspectj Weaver agent. Please refer to our [get started](/get-started) page for more info on how to add dependencies to
your project and starting your application with the AspectJ Weaver.


Configuration
-------------

First, include the Kamon(StatsD) extension under the `akka.extensions` key of your configuration files as shown here:

```scala
akka {
  extensions = ["kamon.statsd.StatsD"]
}
```

Then, tune the configuration settings according to your needs. Here is the `reference.conf` that ships with kamon-statsd
which includes a brief explanation of each setting:

```
kamon {
  statsd {
    # Hostname and port in which your StatsD is running. Remember that StatsD packets are sent using UDP and
    # setting unreachable hosts and/or not open ports wont be warned by the Kamon, your data wont go anywhere.
    hostname = "127.0.0.1"
    port = 8125

    # Interval between metrics data flushes to StatsD. It's value must be equal or greater than the
    # kamon.metrics.tick-interval setting.
    flush-interval = 1 second

    # Max packet size for UDP metrics data sent to StatsD.
    max-packet-size = 1024 bytes

    # Subscription patterns used to select which metrics will be pushed to StatsD. Note that first, metrics
    # collection for your desired entities must be activated under the kamon.metrics.filters settings.
    includes {
      actor = [ "*" ]
      trace = [ "*" ]
    }

    simple-metric-key-generator {
      # Application prefix for all metrics pushed to StatsD. The default namespacing scheme for metrics follows
      # this pattern:
      #    application.host.entity.entity-name.metric-name
      application = "kamon"
    }
  }
}
```


Integration Notes
-----------------

* Contrary to many StatsD client implementations, we don't flush the metrics data as soon as the measurements are taken
  but instead, all metrics data is buffered by the `Kamon(StatsD)` extension and flushed periodically using the
  configured `kamon.statsd.flush-interval` and `kamon.statsd.max-packet-size` settings.
* Currently only Actor and Trace metrics are being sent to StatsD.
* All timing measurements are sent in nanoseconds, make sure you correctly set the scale when plotting or using the
  metrics data.
* It is advisable to experiment with the `kamon.statsd.flush-interval` and `kamon.statsd.max-packet-size` settings to
  find the right balance between network bandwidth utilization and granularity on your metrics data.



Visualization and Fun
---------------------

StatsD is widely used and there are many integrations available, even alternative implementations that can receive UDP
messages with the StatsD protocol, you just have to pick the option that best suits you. For our internal testing we
choose to use [Graphite](http://graphite.wikidot.com/) as the StatsD backend and [Grafana](http://grafana.org) to create
beautiful dashboards with very useful metrics. Have an idea of how your metrics data might look like in Grafana with the
screenshot bellow or use our [docker image](https://github.com/kamon-io/docker-grafana-graphite) to get up and running
in a few minutes and see it with your own metrics!

![statsD](/assets/img/kamon-statsd-grafana.png "Grafana Screenshot")
