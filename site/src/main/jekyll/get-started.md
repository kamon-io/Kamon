---
title: Kamon | Get Started
layout: default
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
libraryDependencies += "io.kamon" % "kamon-core" % "0.0.15"
```

Then, add any additional module you need:

* kamon-core (only compatible with Akka 2.2.x)
* kamon-spray (only compatible with Spray 1.2.x)
* kamon-statsd
* kamon-newrelic


Second: Start your app with the AspectJ Weaver
----------------------------------------------

Starting your app with the AspectJ weaver is dead simple, just add the `-javaagent` JVM startup parameter pointing to
the weaver's file location and you are done:

```
-javaagent:/path-to-aspectj-weaver.jar
```

In case you want to keep the AspectJ related settings in your build and enjoy using `run` from the console, take a look
at the [sbt-aspectj](https://github.com/sbt/sbt-aspectj/) plugin.


Third: Enjoy!
-------------

Refer to module's documentation to find out more about core concepts like [tracing](/core/tracing/),
[metrics](/core/metrics/) and [logging](/core/logging/), and learn how to report your metrics data to external services
like [StatsD](/statsd/) and [New Relic](/newrelic/).
