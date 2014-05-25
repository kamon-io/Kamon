---
title: kamon | NewRelic Module | Documentation
layout: documentation
---

NewRelic Module
===============

If you are a Newrelic user and tried to start start your app using the Newrelic agent you probably noticed a crude reality:
nothing is shown in your dashboard, no web transactions are recognized and errors are not reported for your Spray applications.
Don't even think about detailed traces for the slowest transactions.

We love Spray, and we love Newrelic, we couldn't leave this happening anymore!

Currently the Newrelic Module works together with the Spray Module to get information about your Web Transactions and send
that information to Newrelic servers as a aggregate to the data already colected by Newrelic's Agent. Currently the data
being reported is:

- Time spent for Web Transactions: Also known as `HttpDispatcher` time, represents the total time taken to process a web
transaction, from the moment the `HttpRequest` is received by spray-can, to the moment the answer is sent to the IO layer.
- Apdex
- Errors

Differentiation between JVM and External Services is coming soon, as well as actor metrics and detailed traces.



Installation
-------------

To use the Newrelic module just make sure you put the `kamon-newrelic` and `kamon-spray` libraries in your classpath and
start your application with both, the Aspectj Weaver and Newrelic agents. Please refer to our [get started](/get-started) page
for more info on how to add the AspectJ Weaver and the [Newrelic Agent Installations Instructions](https://docs.newrelic.com/docs/java/new-relic-for-java#h2-installation).


Configuration
-------------

Currently you will need to add a few settings to your `application.conf` file for the module to work:

```scala
akka {
  // Custom logger for NewRelic that takes all the `Error` events from the event stream and publish them to NewRelic
  loggers = ["akka.event.slf4j.Slf4jLogger", "kamon.newrelic.NewRelicErrorLogger"]
  // Make sure the NewRelic extension is loaded with the ActorSystem
  extensions = ["kamon.newrelic.NewRelic"]
}

kamon {
  newrelic {
    // These values must match the values present in your newrelic.yml file.
    app-name = "KamonNewRelicExample[Development]"
    license-key = 0123456789012345678901234567890123456789
  }
}
```


Let's see it in Action!
-----------------------

Let's create a very simple Spray application to show what you should expect from this module. The entire application code
is at [Github](https://github.com/kamon-io/Kamon/tree/master/kamon-examples/kamon-newrelic-example).

```scala
import akka.actor.ActorSystem
import spray.routing.SimpleRoutingApp

object NewRelicExample extends App with SimpleRoutingApp {

 implicit val system = ActorSystem("kamon-system")

 startServer(interface = "localhost", port = 8080) {
   path("helloKamon") {
     get {
       complete {
         <h1>Say hello to Kamon</h1>
       }
     }
   } ~
   path("helloNewRelic") {
     get {
       complete {
         <h1>Say hello to NewRelic</h1>
       }
     }
   }
 }
}
```

As you can see, this is a dead simple application: two paths, different responses for each of them. Now let's hit it hard
with Apache Bench:

```bash
ab -k -n 200000 http://localhost:8080/helloKamon
ab -k -n 200000 http://localhost:8080/helloNewRelic
```

After a couple minutes running you should start seeing something similar to this in your dashboard:

![newrelic](/assets/img/newrelic.png "NewRelic Screenshot")

<div class="alert alert-info">
Note: Don't think that those numbers are wrong, Spray is that fast!
</div>


Limitations
-----------
* The first implementation only supports a subset of NewRelic metrics


Licensing
---------
NewRelic has [its own, separate licensing](http://newrelic.com/terms).

