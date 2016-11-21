Annotation Module ![Build Status](https://travis-ci.org/kamon-io/kamon-annotation.svg?branch=master)
==========================

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kamon-io/Kamon?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

***kamon-annotation*** [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-play-25_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-annotation_2.11)

The Annotation module provides a set of annotations that allow you to easily integrate Kamon's metrics and tracing
facilities with your application.

The <b>kamon-annotation</b> module require you to start your application using the AspectJ Weaver Agent. Kamon will warn
you at startup if you failed to do so.


Enabling Annotation Support
---------------------------

Besides starting your application with the AspectJ Weaver Agent, there are two additional steps required to use
annotations. First you must add the `@EnableKamon` annotation to the classes you would like to be scanned for any of the
provided annotations, otherwise the rest of the annotations described bellow wont be able to work at all.

Manipulating Traces and Segments
--------------------------------

Delimiting traces and segments are one of the most basic tasks you would want to perform to start monitoring your
application using Kamon, and the `@Trace` and `@Segment` annotations allow you to do just that:

* __@Trace__: when a method is marked with this annotation a new [Trace] will be started every time the method is called
and automatically finished once the method returns. Also, the generated `TraceContext` becomes the current context while
the method is executing, making it possible to propagate it at will.
* __@Segment__: when a method is marked with this annotation a new [Segment] will be created only if there is a current
`TraceContext` while the annotated method is called.

Manipulating Instruments
------------------------

Additionally to manipulating traces and segments, the `kamon-annotation` module provides annotations that can be used
to create Counters, Histograms and MinMaxCounters that are automatically updated as the annotated methods execute. These
annotations are:

* __@Time__: when a method is marked with this annotation Kamon will create a Histogram tracking the latency of each
invocation to the method. Please keep in mind that in most situations you might want to use `@Segment` if you are tracking
some functionality that is executed within a trace.

* __@Histogram__: when a method is marked with this annotation Kamon will create a Histogram that stores the values
returned every time the method is invoked. Obviously, only methods returning numeric values are accepted.

* __@Count__: when a method is marked with this annotation Kamon will be create a Counter and automatically increment it
every time the method is invoked.

* __@MinMaxCount__: when a method is marked with this annotation Kamon will be create a MinMaxCounter and automatically
increment it every time method is invoked and decremented when the method returns.


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
