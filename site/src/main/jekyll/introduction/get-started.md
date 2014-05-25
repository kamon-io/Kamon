---
title: Kamon | Get Started
layout: documentation
---

Get Started with Kamon
======================

Kamon is distributed as a core and a set of modules that you include in your application classpath. This modules contain
all the required pointcuts and advices (yeap, Kamon uses Aspectj!) for instrumenting Akka actors message passing,
dispatchers, futures, Spray components and much more.

To get started just follow this steps:


First: Include the modules you want in your project.
----------------------------------------------------

All Kamon components are available through Sonatype and Maven Central and no special repositories need to be configured.
If you are using SBT, you will need to add something like this to your build definition:

```scala
libraryDependencies += "io.kamon" % "kamon-core" % "0.3.0"
```

Then, add any additional module you need:

* kamon-core
* kamon-spray
* kamon-statsd
* kamon-newrelic

### Compatibility Notes: ###

* 0.3.x releases are compatible with Akka 2.3, Spray 1.3 and Play 2.3-M1.
* 0.2.x releases are compatible with Akka 2.2, Spray 1.2 and Play 2.2.


Second: Start your app with the AspectJ Weaver
----------------------------------------------

Starting your app with the AspectJ weaver is dead simple, just add the `-javaagent` JVM startup parameter pointing to
the weaver's file location and you are done:

```
-javaagent:/path-to-aspectj-weaver.jar
```

In case you want to keep the AspectJ related settings in your build and enjoy using `run` from the console, take a look
at the [sbt-aspectj] plugin.


Third: Enjoy!
-------------

Refer to module's documentation to find out more about core concepts like [tracing], [metrics] and [logging], and learn
how to report your metrics data to external services like [StatsD], [Datadog] and [New Relic].


[sbt-aspectj]: https://github.com/sbt/sbt-aspectj/
[tracing]: /core/tracing/basics/
[metrics]: /core/metrics/basics/
[logging]: /core/tracing/logging/
[StatsD]: /backends/statsd/
[Datadog]: /backends/datadog/
[New Relic]: /backends/newrelic/