---
title: Kamon | Core | Documentation
layout: default
---

Metrics
=======

Some intro about metrics

Philosophy
----------

Back in the day, the most common approach to get metrics out of an Akka/Spray application for production monitoring was
doing manual instrumentation: select your favorite metrics collection library, wrap you messages with some useful metadata,
wrap your actor's receive function with some metrics measuring code and, finally, push that metrics data out to somewhere
you can keep it, graph it and analyze it whenever you want.

Each metrics collection library has it's own strengths and weaknesses, and each developer has to choose wisely according to the
requirements they have in hand, leading them in different paths as they progress with their applications. Each path has
different implications with regards to introduced overhead and latency, metrics data accuracy and memory consumption. Kamon takes this
responsibility out of the developer and tries to make the best choice to provide high performance metrics collection instruments
while keeping the inherent overhead as low as possible.

Kamon tries to select the best possible approach, so you don't have to.




Metrics Collection and Flushing
-------------------------------

All the metrics infrastructure in Kamon lives around two concepts: collection and flushing. Metrics collection happens in real time, as soon
as the information is available for being recorded. Let's see a simple example: as soon as a actor finishes processing a
message, Kamon knows the elapsed time for processing that specific message and it is recorded right away. If you have millions
of messages passing through your system, then millions of measurements will be taken.

Flushing happens recurrently after a fixed amount of time has passed, a tick. Upon each tick, Kamon will collect all
measurements recorded since the last tick, flush the collected data and reset all the instruments to zero. Let's explore
a little bit more on how this two concepts are modeled inside Kamon.

SIMPLE CLASS DIAGRAM WITH COLLECTION AND FLUSHING SIDES

