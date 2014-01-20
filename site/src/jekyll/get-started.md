---
title: Kamon | Get Started
layout: default
---

Get Started with Kamon
======================

Kamon is distrubuted as a set of libraries that you include in your application classpath. This libraries contain all the
required pointcuts and advices (yeap, Kamon uses Aspectj!) for instrumenting Akka actors message passing, dispatchers, futures,
Spray components and much more.

To get started just follow this steps:


First: Include the modules you want in your project.
----------------------------------------------------

All Kamon libraries are available through the official Kamon repository:

```scala
    "Kamon Repository" at "http://repo.kamon.io"
```

Then, add the libraries to your project. If you are using SBT this minimal build.sbt file should be helpful:

```scala

resolvers += "Kamon Repository" at "http://repo.kamon.io"

libraryDependencies += "kamon" %%  "kamon-core" % "0.0.11"

```

Additionally you can add any modules you want to your app:

- kamon-core
- kamon-spray
- kamon-newrelic
- kamon-dashboard (coming soon)


Second: Start your app with the AspectJ Weaver
----------------------------------------------

Starting your app with the AspectJ weaver is dead simple, just add the `-javaagent` JVM startup parameter pointing to the
weaver's file location and you are done:

```
-javaagent:/path-to-newrelic-agent.jar
```

In case you want to keep the AspectJ related settings in your build and enjoy using `run` from the console, take a look at
the [sbt-aspectj](https://github.com/sbt/sbt-aspectj/) plugin.


Third: Enjoy!
-------------

Refer to modules documentation (coming soon) to find out what Kamon is doing for you.