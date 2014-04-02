---
title: Kamon | StatsD | Documentation
layout: default
---

What is StatsD?
=======

StatsD is a simple network daemon that continuously receives metrics pushed over UDP and periodically sends aggregate metrics to upstream services
like Graphite. Because it uses UDP, clients (for example, web applications) can ship metrics to it very fast with little to no overhead.
This means that a user can capture multiple metrics for every request to a web application, even at a rate of thousands of requests per second.
Request-level metrics are aggregated over a flush interval (default 10 seconds) and pushed to an upstream metrics service.