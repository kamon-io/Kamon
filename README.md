Annotation Module ![Build Status](https://travis-ci.org/kamon-io/kamon-annotation.svg?branch=kanela)
==========================

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kamon-io/Kamon?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-annotation_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-annotation_2.12)

The Annotation module provides a set of annotations that allow you to easily integrate Kamon's metrics and tracing
facilities with your application.

The <b>kamon-annotation</b> module require you to start your application using the Kanela Agent. 


### Getting Started

The Kamon Annotation module is currently available for Scala 2.11, 2.12 and 2.13.

Supported releases and dependencies are shown below.

| kamon-annotation  | status | jdk  | scala            
|:------:|:------:|:----:|------------------
|  2.0.0 | stable | 1.8+ | 2.11, 2.12, 2.13  

To get started with SBT, simply add the following to your `build.sbt` file:

```scala
libraryDependencies += "io.kamon" %% "kamon-annotation-api" % "2.0.1"
```

Initial Setup
-------------

It is necessary to tell Kamon where to search for annotated classes using the `kanela.modules.annotation.within` configuration
setting. For example, if you want to process annotations on all classes withing the `my.company` package, the following
settings should be added to your `application.conf` file:

```
kanela.modules.annotation {
  within += "my.company.*"
}
```

Manipulating Traces
-------------------

Creating Spans is one of the most basic tasks you would want to perform to start monitoring your application using Kamon
and the `@Trace` annotation allow you to do just that:

* __@Trace__: Creates a new Span every time the method is called and automatically finished once the method returns. If
the annotated method returns a Scala Future or a CompletionStage, the Span will be finished when the Future/CompletionStage
finishes.

* __@CustomizeInnerSpan__: Tells Kamon to use the provided customizations when any Span is created within the Scope of
the annotated method. This is specially useful when you want to customize the operation name of Spans that are
automatically created by Kamon (e.g. customizing JDBC operation names)


Manipulating Metrics
------------------------

Additionally to manipulating Spans, this module can automatically track metrics as the annotated methods execute. The
available annotations are:

* __@Count__: Creates a Counter that tracks invocations of the annotated method.

* __@Time__: Creates a Timer that tracks the latency of each invocation of the annotated method. If the annotated method
returns a Scala Future or a CompletionStage, the Timer will be stopped when the Future/CompletionStage finishes.

* __@Histogram__: Creates a Histogram that tracks the values returned by the annotated method. Obviously, only methods
returning numeric values are accepted.

* __@Gauge__: Creates a Gauge that tracks the last returned value by the annotated method.

* __@TrackConcurrency__: Creates a Range Sampler that is incremented when the annotated method starts executing and
decremented when it finishes. If the annotated method returns a Scala Future or a CompletionStage, the Timer will be
stopped when the Future/CompletionStage finishes.


### EL Expression Support ###

The `name` and `tags` properties are evaluated as [EL] expressions for all annotations that manipulate instruments. 

Limitations
-----------

Annotations are not inherited, regardless of them being declared on a parent class or an implemented interface method.
The root causes of that limitation, according to the [JLS], are:

* Non-type annotations are not inherited,
* Annotations on types are only inherited if they have the __@Inherited__ meta-annotation,
* Annotations on interfaces are not inherited irrespective to having the __@Inherited__ meta-annotation.


[instruments]: /core/metrics/instruments/
[JLS]: http://docs.oracle.com/javase/specs/jls/se7/html/jls-9.html#jls-9.6
[Trace]: /core/tracing/core-concepts/#the-tracecontext
[Segment]: /core/tracing/core-concepts/#trace-segments
[Traces]: /core/tracing/trace-context-manipulation/#creating-and-finishing-a-tracecontext
[Segments]: /core/tracing/trace-context-manipulation/#creating-and-finishing-segments
[Limitations]: #limitations
[EL]: https://jcp.org/en/jsr/detail?id=341
