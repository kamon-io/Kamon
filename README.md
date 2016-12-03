Datadog Integration    ![Build Status](https://travis-ci.org/kamon-io/kamon-datadog.svg?branch=master)
==========================

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kamon-io/Kamon?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

***kamon-spm*** [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-datadog_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-datadog_2.11)

Reporting Metrics to Datadog
===========================

[Datadog] is a monitoring service for IT, Operations and Development teams who write and run applications at scale, and
want to turn the massive amounts of data produced by their apps, tools and services into actionable insight.

Installation
------------

Add the `kamon-datadog` dependency to your project and ensure that it is in your classpath at runtime, that's it.
Kamon's module loader will detect that the Datadog module is in the classpath and automatically start it.


Configuration
-------------

By default, this module assumes that you have an instance of the Datadog Agent running in localhost and listening on
port 8125. If that is not the case the you can use the `kamon.datadog.hostname` and `kamon.datadog.port` configuration
keys to point the module at your Datadog Agent installation.

The Datadog module subscribes itself to the entities included in the `kamon.datadog.subscriptions` key. By default, the
following subscriptions are included:

```
kamon.datadog {
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

If you are interested in reporting additional entities to Datadog please ensure that you include the categories and name
patterns accordingly.


### Metric Naming Conventions ###

For all single instrument entities (those tracking counters, histograms, gaugues and min-max-counters) the generated
metric key will follow the `application.instrument-type.entity-name` pattern. Additionaly all tags supplied when
creating the instrument will also be reported.

For all other entities the pattern is a little different: `application.entity-category.entity-name` and a identification
tag using the category and entity name will be used. For example, all mailbox size measurements for Akka actors are
reported under the `application.akka-actor.mailbox-size` metric and a identification tag similar to
`actor:/user/example-actor` is included as well.

Finally, the application name can be changed by setting the `kamon.datadog.application-name` configuration key.

### Metric Units ###

Kamon keeps all timing measurements in nanoseconds and memory measurements in bytes. In order to scale those to other units before sending to datadog, set `time-units` and `memory-units` config keys to desired units. Supported units are:
```
n  - nanoseconds
µs - microseconds
ms - milliseconds
s  - seconds

b  - bytes
kb - kilobytes
mb - megabytes
gb - gigabytes
```
For example,
```
kamon.datadog.time-units = "ms" 
```
will scale all timing measurements to milliseconds right before sending to datadog.

Integration Notes
-----------------

* Contrary to other Datadog client implementations, we don't flush the metrics data as soon as the measurements are
  taken but instead, all metrics data is buffered by the `kamon-datadog` module and flushed periodically using the
  configured `kamon.datadog.flush-interval` and `kamon.datadog.max-packet-size` settings.
* It is advisable to experiment with the `kamon.datadog.flush-interval` and `kamon.datadog.max-packet-size` settings to
  find the right balance between network bandwidth utilisation and granularity on your metrics data.

<img class="img-responsive" src="http://kamon.io/assets/img/datadog-scaling-metrics.png">


Visualization and Fun
---------------------

Creating a dashboard in the Datadog user interface is really simple, just start typing the application name ("kamon" by
default) in the metric selector and all metric names will start to show up. You can also break it down based on the entity
names. Here is a very simple example of a dashboard created with metrics reported by Kamon:

<img class="img-responsive" src="http://kamon.io/assets/img/datadog-dashboard.png">

[Datadog]: http://www.datadoghq.com/
[get started]: /introduction/get-started/
