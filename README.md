# Akka Integration

[![Build Status](https://travis-ci.org/kamon-io/kamon-akka.svg?branch=master)](https://travis-ci.org/kamon-io/kamon-akka)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kamon-io/Kamon?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

***kamon-akka-2.3.x*** [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-akka-23_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-akka_2.11)

***kamon-akka-2.4.x*** [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-akka-24_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-akka_2.11)

Kamon's integration with Akka comes in the form of two modules: `kamon-akka` and `kamon-akka-remote` that bring bytecode
instrumentation to gather metrics and perform automatic `TraceContext` propagation on your behalf.

The <b>kamon-akka</b> module require you to start your application using the AspectJ Weaver Agent. Kamon will warn you at startup if you failed to do so.

Here is a quick list of the functionalities included on each module:

### kamon-akka ###

* __[Actor, Router and Dispatcher Metrics]__: This module hooks into Akka's heart to give you a robust set of metrics
based on the concepts already exposed by our metrics module.
* __[Automatic TraceContext Propagation]__: This allows you to implicitly propagate the `TraceContext` across actor messages
without having to change a single line of code and respecting the "follow the events" rather than "stick to the thread"
convention as described in the [event based threading model section].
* __[Ask Pattern Timeout Warning]__: A utility that logs a warning with additional information when a usage of the Ask
Pattern timesout.


[event based threading model section]: /core/tracing/threading-model-considerations/
[Ask Pattern Timeout Warning]: /integrations/akka/ask-pattern-timeout-warning/
[Actor, Router and Dispatcher Metrics]: /integrations/akka/actor-router-and-dispatcher-metrics/
[Automatic TraceContext Propagation]: /integrations/akka/automatic-trace-context-propagation/
