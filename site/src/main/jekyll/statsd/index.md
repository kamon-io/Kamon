---
title: Kamon | StatsD | Documentation
layout: default
---

What is StatsD?
=======

StatsD is a simple network daemon that continuously receives metrics pushed over UDP and periodically sends aggregate metrics to upstream services
like Graphite. Because it uses UDP, clients can send metrics to it very fast with little to no overhead.
This means that a user can capture multiple metrics for every request to a web application, even at a rate of thousands of requests per second.
Request-level metrics are aggregated over a flush interval (default 10 seconds) and pushed to an upstream metrics service.


Getting Started with StatsD
----------

Installation
-------------

To use the StatsD module just make sure you put the `kamon-statsd` library in your classpath and start your application the Aspectj Weaver and Newrelic agents. Please refer to our [get started](/get-started) page
for more info on how to add the AspectJ Weaver.


Configuration
-------------

Currently you will need to add a few settings to your `application.conf` file for the module to work:

```scala
akka {
  // Make sure the StatsD extension is loaded with the ActorSystem
  extensions = ["kamon.statsd.StatsD"]
}

kamon {
  statsd {
    # Hostname and port in which your StatsD is running. Remember that StatsD packets are sent using UDP and
    # setting unreachable hosts and/or not open ports wont be warned by the Kamon, your data wont go anywhere.
    hostname = "127.0.0.1"
    port = 8125

    # Interval between metrics data flushes to StatsD. It's value must be equal or greater than the
    # kamon.metrics.tick-interval setting.
    flush-interval = 1 second

    # Max packet size in bytes for UDP metrics data sent to StatsD.
    max-packet-size = 1024

    # Subscription patterns used to select which metrics will be pushed to StatsD. Note that first, metrics
    # collection for your desired entities must be activated under the kamon.metrics.filters settings.
    includes {
      actor = [ "*" ]
    }

    simple-metric-key-generator {
      # Application prefix for all metrics pushed to StatsD. The default namespacing scheme for metrics follows
      # this pattern:
      #    application.host.entity.entity-name.metric-name
      application = "Kamon"
    }
  }
}
```
Installing Graphite
----------

In the Graphite documentation we can find the [Graphite overview](http://graphite.readthedocs.org/en/latest/overview.html#what-graphite-is-and-is-not). It sums up Graphite with these two simple points.

* Graphite stores numeric time-series data.
* Graphite renders graphs of this data on demand.

Show data with [Grafana](http://grafana.org)
----------

![statsD](/assets/img/kamon-statsd-grafana.png "Grafana Screenshot")
