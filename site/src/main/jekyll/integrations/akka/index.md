---
title: kamon | Akka Toolkit | Documentation
layout: documentation
---

Akka Module
===

---
Dependencies
---

Apart from scala library kamon depends on:

- aspectj 
- spray-io 
- akka-actor 


Installation
---
Kamon works with SBT, so you need to add Kamon.io repository to your resolvers.

Configuration
---
Just like other products in the scala ecosystem, it relies on the typesafe configuration library. 
    
Since kamon uses the same configuration technique as [Spray](http://spray.io/documentation "Spray") / [Akka](http://akka.io/docs "Akka") you might want to check out the [Akka-Documentation-configuration](http://doc.akka.io/docs/akka/2.1.4/general/configuration.html "Akka Documentation on configuration")
.

In order to see Kamon in action you need first to set up your sbt project.

1) Add Kamon repository to resolvers

```scala
"Kamon Repository" at "http://repo.kamon.io"
```

2) Add libraryDepenency

```scala 
    "kamon" %%  "kamon-spray" % "0.0.11",
```

In addition we suggest to create aspectj.sbt file and add this content

```scala
    import com.typesafe.sbt.SbtAspectj._

    aspectjSettings

    javaOptions <++= AspectjKeys.weaverOptions in Aspectj
```

3) Add to your plugins.sbt in project folder (if you don't have one yet, create the file) and add the Kamon release to the resolver and the aspecj.

```scala
    resolvers += Resolver.url("Kamon Releases", url("http://repo.kamon.io"))(Resolver.ivyStylePatterns)

    addSbtPlugin("com.typesafe.sbt" % "sbt-aspectj" % "0.9.2")
``` 
**application.conf**

```scala
    akka {
      loggers = ["akka.event.slf4j.Slf4jLogger"]
  
        actor {
           debug {
                    unhandled = on
            }
        }
    }
```

Examples
---

TODO: (to be published) The example will start a spray server with akka and logback configuration. Adjust it to your needs. 

Follow the steps in order to clone the repository

1. git clone git://github.com/kamon/kamon.git

2. cd kamon

For the first example run

```bash
    sbt "project kamon-uow-example"
```

In order to see how it works, you need to send a message to the rest service

```bash
    curl -v --header 'X-UOW:YOUR_TRACER_ID' -X GET 'http://0.0.0.0:6666/fibonacci'
```
