System Metrics   ![Build Status](https://travis-ci.org/kamon-io/kamon-system-metrics.svg?branch=master)
==========================

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kamon-io/Kamon?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-system-metrics_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-system-metrics.11)

Our `kamon-system-metrics` module registers a number of entities with the metrics module that track the performance
indicators of both the host machine and the JVM where your application is running.

This module doesn't have any bytecode instrumentation requirement, and its only requirement to work properly is that
the appropriate [Sigar] native library is correctly loaded. To do so, the `kamon-system-metrics` module makes use of the
[sigar-loader] library. If your application uses Sigar for other purposes, it is advisable that you take a look at
[sigar-loader] to simplify the sigar native library provisioning process.


### Getting Started

Kamon sytem-metrics module is currently available for Scala 2.10, 2.11 and 2.12.

Supported releases and dependencies are shown below.

| kamon-system-metrics  | status | jdk  | scala            | akka   |
|:------:|:------:|:----:|------------------|:------:|
|  0.6.5 | stable | 1.7+, 1.8+ | 2.10, 2.11, 2.12  | 2.3.x, 2.4.x |

To get started with SBT, simply add the following to your `build.sbt`
file:

```scala
libraryDependencies += "kamon.io" %% "kamon-system-metrics" % "0.6.5"
``` 

As you might expect, you and any other module can subscribe to all the metrics that are reported by this module using
the `system-metric` category and the entity recorder names described bellow.

By default the `kamon-system-metrics` module starts with Host and JVM metrics enabled, in the case that you want to **enable/disable** one of them, you can configure it this way:

```typesafeconfig
kamon {
  system-metrics {
   #sigar is enabled by default
   sigar-enabled = true

   #jmx related metrics are enabled by default
   jmx-enabled = true
  }
}
```
Host System Metrics
-------------------

We are using [Sigar] to gather all the host system metrics information and this requires us to have a few special
considerations given that [Sigar] instances are not thread-safe and some metrics (like cpu usage metrics) do not work
correctly when updated in intervals of less than a second. In the sections below, you will see histograms tracking
metrics that typically should be recorded with a gauge, but that we couldn't allow because of the need to have a tight
control on timings and thread-safety.

In the case that <b>Sigar</b> can't obtain some metric in the host, we will log a warning indicating the error and the metric name.

### cpu ###
* __user__: a histogram tracking total percentage of system cpu user time.
* __system__: a histogram tracking total percentage of system cpu kernel time.
* __wait__: a histogram tracking total percentage of system cpu io wait time.
* __idle__: a histogram tracking total percentage of system cpu idle time
* __stolen__: a histogram tracking total percentage of system cpu involuntary wait time.


### file-system ###
* __readBytes__: a histogram tracking total number of physical disk reads in bytes.
* __writesBytes__: a histogram tracking total number of physical disk writes in bytes.


### load-average ###
* __one-minute__: a histogram tracking the system load average for the last minute.
* __five-minutes__: a histogram tracking the system load average for the five minutes.
* __fifteen-minutes__: a histogram tracking the system load average for the fifteen minutes.


### memory ###
* __memory-used__: a histogram tracking total used system memory in bytes.
* __memory-cache-and-buffer__: a histogram tracking total memory used in cache and buffers memory in bytes.
* __memory-free__: a histogram tracking total free system memory in bytes.
* __memory-total__: a histogram tracking total system memory capacity in bytes.
* __swap-used__: a histogram tracking total used system swap in bytes.
* __swap-free__: a histogram tracking total used system swap in bytes.


### network ###

All network metrics represent the aggregate of all interfaces available in the host.

* __rx-bytes__: a histogram tracking total number of received packets in bytes.
* __tx-bytes__: a histogram tracking total number of transmitted packets in bytes.
* __rx-errors__: a histogram tracking total number of packets received with errors. This includes too-long-frames errors, ring-buffer overflow errors, etc.
* __tx-errors__: a histogram tracking total number of errors encountered while transmitting packets. This list includes errors due to the transmission being aborted, errors due to the carrier, etc.
* __rx-dropped__: a histogram tracking total number of incoming packets dropped.
* __tx-dropped__: a histogram tracking total number of outgoing packets dropped.


### process-cpu ###
* __process-user-cpu__: a histogram tracking the total percentage of CPU spent by the application process in user space, relative to the overall CPU usage.
* __process-system-cpu__: a histogram tracking the total percentage of CPU spent by the application process in system space, relative to the overall CPU usage.
* __process-cpu__: a histogram tracking the total percentage of CPU spent by the application, relative to the overall CPU usage.


### context-switches ###

The context switches metrics are special in the sense that they are not read using the [Sigar] library but rather reading
the information available in the `/proc/$pid/status` file for Linux systems.

* __context-switches-process-voluntary__: Total number of voluntary context switches related to the current process (one
thread explicitly yield the CPU to another).
* __context-switches-process-non-voluntary__: Total number of involuntary context switches related to the current process
(the system scheduler suspends an active thread, and switches control to a different thread).
* __context-switches-global__:  Total number of context switches across all CPUs.

JVM Metrics
-----------

All JVM-specific metrics are gathered using JMX and all of them are using gauges to record the data. The reported JVM
metrics include:


### \*-garbage-collector ###

Depending on your specific instance configuration, the available garbage collectors will differ, but the same set of
metrics are recorded regardless of the collector in place.

* __garbage-collection-count__: a gauge tracking the number of garbage collections that have ocurred.
* __garbage-collection-time__: a gauge tracking the time spent in garbage collections, measured in milliseconds.


### class-loading ###
* __classes-loaded__: a gauge tracking the number of classes ever loaded by the application.
* __classes-unloaded__: a gauge tracking the number of classes ever unloaded by the application.
* __classes-currently-loaded__: a gauge tracking the number of classes currently loaded by the application.


### heap-memory ###
* __heap-used__: a gauge tracking the amount of heap memory currently being used in bytes.
* __heap-max__: a gauge tracking the maximum amount of heap memory that can be used in bytes.
* __heap-committed__: a gauge tracking the amount of memory that is committed for the JVM to use in bytes.


### non-heap-memory ###
* __non-heap-used__: a gauge tracking the amount of non-heap memory currently being used in bytes.
* __non-heap-max__: a gauge tracking the maximum amount of non-heap memory that can be used in bytes.
* __non-heap-committed__: a gauge tracking the amount of non-heap memory that is committed for the JVM to use in bytes.


### threads ###
* __daemon-thread-count__: a gauge tracking the total number of daemon threads running in the JVM.
* __peak-thread-count__: a gauge tracking the peak number of threads running in the JVM since it started.
* __thread-count__: a gauge tracking the total number of live threads in the JVM, including both daemon and non-daemon threads.


[Sigar]: https://github.com/hyperic/sigar
[sigar-loader]: https://github.com/kamon-io/sigar-loader
