---
title: kamon | NewRelic Module | Documentation
layout: default
---

NewRelic Module
===
A simple module to report some application metrics like External Services, Errors and Apdex. 

Configuration
---

**In order to see Kamon in action you just follow this Simple Example:**


**1)** All Kamon libraries are available through the official Kamon repository:

```scala
"Kamon Repository" at "http://repo.kamon.io"
```

**2)** Add the libraries to your project:

```scala
resolvers += "Kamon Repository" at "http://repo.kamon.io"

"kamon" %%  "kamon-core" % "0.0.12"

"kamon" %%  "kamon-spray" % "0.0.12"

"kamon" %%  "kamon-newrelic" % "0.0.12"
```

Also you need add this lines into the `build.sbt` in order to configure the [sbt-aspectj](https://github.com/sbt/sbt-aspectj/) plugin:

```scala
import com.typesafe.sbt.SbtAspectj._

aspectjSettings

javaOptions <++= AspectjKeys.weaverOptions in Aspectj
```

**3)** Add to your `plugins.sbt` in project folder (if you don't have one yet, create the file) and add this content:

```scala
resolvers += "Kamon Releases" at "http://repo.kamon.io"

addSbtPlugin("com.ivantopo.sbt" %% "sbt-newrelic" % "0.0.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-aspectj" % "0.9.4")
``` 
**4)** Our Reactive Application:

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

**5)** In addition, you have to provide some information about NewRelic configuration in the `application.conf`:

```scala
akka {
  extensions = ["kamon.newrelic.NewRelic"]
}

kamon {
  newrelic {
    app-name = "KamonNewRelicExample[Development]"
    license-key = <<Key>>
  }
}
```

**6)** Add the [NewRelic](http://newrelic.com/) Agent:

```scala
-javaagent:/path-to-newrelic-agent.jar -Dnewrelic.environment=production -Dnewrelic.config.file=/path-to-newrelic.yml
```
In case you want to keep the NewRelic Agent related setting, take a look at [NewRelic](https://docs.newrelic.com/docs/java/new-relic-for-java)


**7)** To see how it works, you need to send a messages to the rest services

```bash
ab -k -n 2000 http://localhost:8080/helloNewRelic
```
### Example
This and others examples are found in the [GitHub](https://github.com/kamon-io/Kamon/tree/master/examples/) Kamon repository inside examples folder.

### Screenshot

![newrelic](/assets/img/newrelic.png "NewRelic Screenshot")


## Limitations
* The first implementation only supports a subset of NewRelic metrics

## Licensing

NewRelic has [its own, separate licensing](http://newrelic.com/terms).

