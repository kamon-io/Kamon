---
title: Kamon | Changelog
layout: default
---

Changelog
=========

<hr>
Version 0.3.0/0.2.0 <small>(2014-04-24)</small>
--------------------------------

* Same feature set as 0.0.15 but now available for Akka 2.2 and Akka 2.3:
   * 0.3.0 is compatible with Akka 2.3, Spray 1.3 and Play 2.3-M1.
   * 0.2.0 is compatible with Akka 2.2, Spray 1.2 and Play 2.2.

<hr>
Version 0.0.15 <small>(2014-04-10)</small>
---------------------------

* kamon
    * Now publishing to Sonatype and Maven Central
    * `reference.conf` files are now "sbt-assembly merge friendly"

* kamon-core
    * Control of AspectJ weaving messages through Kamon configuration
    * Avoid the possible performance issues when calling `MessageQueue.numberOfMessages` by keeping a external counter.

* kamon-statsd
    * Now you can send Actor and Trace metrics to StatsD! Check out our [StatsD documentation](/statsd/) for more
      details.

* kamon-play (Experimental)
    * Experimental support to trace metrics collection, automatic trace token propagation and HTTP Client request
      metrics is now available for Play! applications.


<hr>
Version 0.0.14 <small>(2014-03-17)</small>
---------------------------
* kamon-core
    * Improved startup times
    * Remake of trace metrics collection
    * Support for custom metrics collection (Experimental)

* kamon-play
    * Initial support (Experimental)

* site
    * [logging](/core/logging/) (WIP)
    * [tracing](/core/tracing/) (WIP)
