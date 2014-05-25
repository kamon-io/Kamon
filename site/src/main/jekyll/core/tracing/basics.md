---
title: Kamon | Core | Documentation
layout: documentation
---

Traces
======

A trace is a story, told by some events in your application that explain how the execution of a particular portion of
functionality went during a single invocation. For example, if in order to fulfil a `GET` request to the `/users/kamon`
resource, a application sends a message to an actor, which reads the user data from a database and sends a message back
with the user information to finish the request, all those interactions would be considered as part of the same trace.

If the application described above were to handle a hundred clients requesting for user's details, there might be a
handful of database access actors handling those requests. When the dispatcher gives an actor some time to execute, it
will process as many messages as possible (as per dispatcher configuration) before the Thread is taken away from it, but
during that time it is incorrect to say that either the actor or the Thread are tied to a trace, because each message
might come from a different source which is probably waiting for a different response.

Back in the day tracing used to be simpler: if you use single dedicated `Thread` during the execution of a request and
everything related to that request happens in that single `Thread`, then you could use a `ThreadLocal` and store all the
valuable information you want about the processing of that request from anywhere in the codebase and flush it all when
the request is finished. Sounds easy, right?, hold on that thought, it isn't that easy in the reactive world!

When developing reactive applications on top of Akka the perspective of a trace changes from "thread local" to "event
local" and in order to cope with this Kamon provides the notion of a `TraceContext` to group all related events and
collect the information we need about them. Once a `TraceContext` is created, Kamon will propagate it automatically
under specific conditions and once the `TraceContext` is finished, all the gathered information is flushed. The
`TraceContext` is effectively stored in a ThreadLocal, but only during the processing of certain specific events and
then it is cleared out to avoid propagating it to unrelated events.


Starting a `TraceContext`
-------------------------

The `TraceRecorder` companion object provides a simple API to create, propagate and finish a `TraceContext`. To start a
new context use the `TraceRecorder.withNewTraceContext(..)` method. Let's dig into this with a simple example:

Suppose you want to trace a process that involves a couple actors, and you want to make sure all related events become
part of the same `TraceContext`. Our actors might look like this:

{% include_code kamon/docs/trace/SimpleContextPropagation.scala start:47 end:63 linenos:false %}

You should feel familiar with this code, there is nothing new there. Let's spawn an `UpperCaser` actor and send it five
string messages and see the output on the log file:

```
22:24:07.197 INFO  [undefined][akka://simple-context-propagation/user/upper-caser] Upper casing [Hello without context]
22:24:07.198 INFO  [undefined][akka://simple-context-propagation/user/upper-caser] Upper casing [Hello without context]
22:24:07.198 INFO  [undefined][akka://simple-context-propagation/user/upper-caser/length-calculator] Calculating the length of: [HELLO WITHOUT CONTEXT]
22:24:07.199 INFO  [undefined][akka://simple-context-propagation/user/upper-caser] Upper casing [Hello without context]
22:24:07.200 INFO  [undefined][akka://simple-context-propagation/user/upper-caser] Upper casing [Hello without context]
22:24:07.200 INFO  [undefined][akka://simple-context-propagation/user/upper-caser] Upper casing [Hello without context]
22:24:07.204 INFO  [undefined][akka://simple-context-propagation/user/upper-caser/length-calculator] Calculating the length of: [HELLO WITHOUT CONTEXT]
22:24:07.205 INFO  [undefined][akka://simple-context-propagation/user/upper-caser/length-calculator] Calculating the length of: [HELLO WITHOUT CONTEXT]
22:24:07.205 INFO  [undefined][akka://simple-context-propagation/user/upper-caser/length-calculator] Calculating the length of: [HELLO WITHOUT CONTEXT]
22:24:07.206 INFO  [undefined][akka://simple-context-propagation/user/upper-caser/length-calculator] Calculating the length of: [HELLO WITHOUT CONTEXT]
```

Can you tell which log statement from `LengthCalculator` corresponds to each log statement from `UpperCaser`?, seems
easy to figure it out manually in this case, but as the number of events happening concurrently in your app grows it
becomes harder to answer that question without some extra help. Let's see how Kamon can help us in this situation:

{% include_code kamon/docs/trace/SimpleContextPropagation.scala start:38 end:40 linenos:false %}

When using the `TraceRecorder.withNewTraceContext(..)` method, Kamon will create a new `TraceContext` and make it
available during the execution of the piece of code passed as argument. This time, we are sending a message to an actor
which happens to be one of the situations under which Kamon will automatically propagate a `TraceContext`, so we can
expect the current context to be available to the actor when processing the message we just sent, and
<strong>only</strong> when processing that message. Let's repeat the exercise of sending five messages to this actor,
now doing it with a new `TraceContext` each time and look at the log:

```
22:24:07.223 INFO  [Ivan-Topolnjaks-MacBook-Pro.local-1][akka://simple-context-propagation/user/upper-caser] Upper casing [Hello World with TraceContext]
22:24:07.224 INFO  [Ivan-Topolnjaks-MacBook-Pro.local-2][akka://simple-context-propagation/user/upper-caser] Upper casing [Hello World with TraceContext]
22:24:07.225 INFO  [Ivan-Topolnjaks-MacBook-Pro.local-1][akka://simple-context-propagation/user/upper-caser/length-calculator] Calculating the length of: [HELLO WORLD WITH TRACECONTEXT]
22:24:07.225 INFO  [Ivan-Topolnjaks-MacBook-Pro.local-3][akka://simple-context-propagation/user/upper-caser] Upper casing [Hello World with TraceContext]
22:24:07.226 INFO  [Ivan-Topolnjaks-MacBook-Pro.local-4][akka://simple-context-propagation/user/upper-caser] Upper casing [Hello World with TraceContext]
22:24:07.227 INFO  [Ivan-Topolnjaks-MacBook-Pro.local-5][akka://simple-context-propagation/user/upper-caser] Upper casing [Hello World with TraceContext]
22:24:07.227 INFO  [Ivan-Topolnjaks-MacBook-Pro.local-2][akka://simple-context-propagation/user/upper-caser/length-calculator] Calculating the length of: [HELLO WORLD WITH TRACECONTEXT]
22:24:07.228 INFO  [Ivan-Topolnjaks-MacBook-Pro.local-3][akka://simple-context-propagation/user/upper-caser/length-calculator] Calculating the length of: [HELLO WORLD WITH TRACECONTEXT]
22:24:07.228 INFO  [Ivan-Topolnjaks-MacBook-Pro.local-4][akka://simple-context-propagation/user/upper-caser/length-calculator] Calculating the length of: [HELLO WORLD WITH TRACECONTEXT]
22:24:07.229 INFO  [Ivan-Topolnjaks-MacBook-Pro.local-5][akka://simple-context-propagation/user/upper-caser/length-calculator] Calculating the length of: [HELLO WORLD WITH TRACECONTEXT]
```

Can you tell which log statement from `LengthCalculator` corresponds to each log statement from `UpperCaser` now?, it
has become a no brainer: each `TraceContext` created by Kamon gets a unique token that we are including in the log
patterns (the first value between square brackets) and with that small but important piece of information the relation
between each log line is clear.

Just by logging the trace token you can get a lot of visibility and coherence in the information available on your logs,
please  refer to the [logging](../logging/) section to learn how to include the trace token in your logs.


Rules for `TraceContext` Propagation
------------------------------------

* Actor creation.
* Sending messages to a Actor.
* Using ActorLogging.
* Creating and transforming a Future.
