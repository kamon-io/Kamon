---
title: Kamon | Core | Documentation
layout: default
---

At it's core, Kamon aims to provide three basic functionalities: metrics, message tracing and a subscription protocol
to periodically get that valuable information from Kamon and do whatever you want with it. Typically you would push
your data into a metrics repository that may provide you a dashboard and historic metrics analitics, but the door
is open for you to be creative, it is your data anyway.


Metrics
-------





Traces
------

In Kamon, a Trace is a group of events that are related to each other which together form a meaningful piece of functionality
for a given application. For example, if in order to fulfill a `GET` request to the `/users/kamon` resource, a application
sends a message to a actor, which reads the user data from a database and send a message back with the user response to
finish the request, all those interactions would be considered part of the same `TraceContext`. 

Back in the day tracing used to be simpler: if you create a Thread per request and manage everything related to that request
in that single Thread, then using a ThreadLocal you can store all the valuable information you want about that request and
flush it all when the request is fulfilled. Sounds easy, right?, hold on that thought, we will disprove it soon.

When developing reactive applications on top of Akka the perspective of a trace changes from thread local to event local. 
If the system described above were to handle a hundred clients requesting for user's details, you might have a handful
of database access actors handling those requests. The load might be distributed across those actors, and within each actor
some messages will be procesed in the same Thread, then the dispatcher might schedule the actor to run in a different Thread,
but still, even while many messages can be processed in the same Thread, they are likely to be completely unrelated.

In order to cope with this situation Kamon provides with the notion of a `TraceContext` to group all related events and
collect the information we need about them. Once a `TraceContext` is created, Kamon will propagate it when new events are
generated within the context and once the `TraceContext` is finished, all the gathered information is flushed.