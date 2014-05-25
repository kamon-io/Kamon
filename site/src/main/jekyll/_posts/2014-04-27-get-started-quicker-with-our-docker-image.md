---
layout: post
title: Get started quicker with our docker image
date: 2014-04-27
categories: teamblog
tags: announcement
---

We are very excited to see people adopting Kamon as their monitoring tool for reactive applications and, of course, we
want to keep growing both in users base and features. According to our site metrics, the most visited section is the one
describing our [StatsD module], that made us think, what can we do to make it easier for people to get started
with Kamon and StatsD?, well, that's an easy question to answer: build a package containing all the required
infrastructure and plumping, and let the users just focus on what cares to them, their apps and their metrics. That's
why today we are publishing a Docker image with all that you need to get started in a few minutes!


The image contains a sensible default configuration of StatsD, Graphite and Grafana, and comes bundled with a example
dashboard that gives you the basic metrics currently collected by Kamon for both Actors and Traces. There are two ways
for using this image:


### Using the Docker Index ###

This image is published under [Kamon's repository on the Docker Index](https://index.docker.io/u/kamon/) and all you
need as a prerequisite is having Docker installed on your machine. The container exposes the following ports:

- `80`: the Grafana web interface.
- `8125`: the StatsD port.
- `8126`: the StatsD administrative port.

To start a container with this image you just need to run the following command:

```bash
docker run -d -v /etc/localtime:/etc/localtime:ro -p 80:80 -p 8125:8125/udp -p 8126:8126 --name kamon-grafana-dashboard kamon/grafana_graphite
```

If you already have services running on your host that are using any of these ports, you may wish to map the container
ports to whatever you want by changing left side number in the `-p` parameters. Find more details about mapping ports
in the [Docker documentation](http://docs.docker.io/use/port_redirection/#port-redirection).


### Building the image yourself ###

The Dockerfile and supporting configuration files are available in our [Github repository](https://github.com/kamon-io/docker-grafana-graphite).
This comes specially handy if you want to change any of the StatsD, Graphite or Grafana settings, or simply if you want
to know how tha image was built. The repo also has `build` and `start` scripts to make your workflow more pleasant.


### Using the Dashboard ###

Once your container is running all you need to do is open your browser pointing to the host/port you just published and
play with the dashboard at your wish. We hope that you have a lot of fun with this image and that it serves it's
purpose of making your life easier. This should give you an idea of how the dashboard looks like when receiving data
from one of our toy applications:

<img class="img-responsive" src="/assets/img/kamon-statsd-grafana.png">


[StatsD module]: /backends/statsd/