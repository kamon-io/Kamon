---
title: Kamon | Core | Documentation
layout: documentation
---

Logging
=======

Kamon provides a very simple way to make sure that the trace token available when the log statement was executed is
included in your logs, no matter if you are logging synchronously or asynchronously. Kamon provides built in support
for logging with Logback, but extending the support to any other logging framework should be a trivial task.

When using `ActorLogging` all logging events are sent to your actor system's event stream and then picked up by your
registered listeners for actual logging. Akka captures the actor, thread and timestamp from the instant in which the
event was generated and makes that info available when performing the actual logging. As an addition to this, Kamon
attaches the `TraceContext` that is present when creating the log events and makes it available when the actual logging
is performed. If you are using the loggers directly then the `TraceContext` should be already available.

`TraceRecorder.currentContext` gives you access to the currently `TraceContext`, so the following expression gives you
the trace token for the currently available context:

```scala
TraceRecorder.currentContext.map(_.token)
```

Kamon already packs a Logback converter that you can register in your `logback.xml` file and use in your logging
patterns as show bellow:

```xml
<configuration scan="true">
    <conversionRule conversionWord="traceToken" converterClass="kamon.trace.logging.LogbackTraceTokenConverter" />
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%date{HH:mm:ss.SSS} %-5level [%traceToken][%X{akkaSource}] %msg%n</pattern>
        </encoder>
    </appender>

    <root level="debug">
        <appender-ref ref="STDOUT" />
    </root>

</configuration>
```
