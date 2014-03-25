---
title: kamon | Akka Toolkit | Documentation
layout: default
---

Akka Module
===
Kamon will help you in monitoring your reactive akka application. Let's go to create our first akka application with kamon. In the near future you will find Kamon template in typesafe Activator.

---
Dependencies
---

Apart from scala library, kamon depends on:

- aspectj 
- akka-actor
- spray (only for spray enabled apps)
- newrelic (only for new relic enabled apps)


Installation
---
Let's start with an empty sbt project like kamon-akka-example (you will find the project in kamon-example folder). Edit config files with your favorite editor like vim :)

```
mkdir -p kamon-akka-example/project
mkdir -p kamon-akka-example/src/main/scala
mkdir -p kamon-akka-example/src/main/resources

touch kamon-akka-example/build.sbt

touch kamon-akka-example/project/build.properties
touch kamon-akka-example/project/plugins.sbt

touch kamon-akka-example/src/main/application.conf
```

Edit kamon-akka-example/project/build.properties and add sbt version

```
sbt.version=0.12.3
```

Edit kamon-akka-example/build.sbt

```
import sbt._

import sbt.Keys._

name := "kamon-akka-example"
 
version := "1.0"
 
scalaVersion := "2.10.2"

resolvers += "Kamon repo" at "http://repo.kamon.io"

libraryDependencies ++= Seq(
    "kamon"             % "kamon-core"      % "0.0.14",
    "com.typesafe.akka" % "akka-slf4j"      % "2.2.3",
    "org.slf4j"         % "slf4j-api"       % "1.7.5",
    "ch.qos.logback"    % "logback-classic" % "1.0.13",
    "org.aspectj"       % "aspectjweaver"   % "1.7.4"
    )
```

Edit plugins.sbt (kamon-akka-example/project), note that sbt-idea is not required, however, if you want to be a happy coder, you should give a try to intellij and run sbt gen-idea!

```
resolvers += "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"

resolvers += "Kamon Releases" at "http://repo.kamon.io"

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.5.2")
```
Finally, step into kammon-akka-example, you can run your app like any scala app, passing the aspectjweaver.jar version 1.7.4/1.7.2 to the jvm

```
-javaagent:/path/to/file/aspectjweaver-1.7.4.jar 
```

Take a look to [get started](http://kamon.io/get-started/) for more information with the javaagent.


Akka Configuration
---
Just like other products in the scala ecosystem, it relies on the typesafe configuration library. 
    
Since kamon uses the same configuration technique as [Spray](http://spray.io/documentation "Spray") / [Akka](http://akka.io/docs "Akka") you might want to check out the [Akka-Documentation-configuration](http://doc.akka.io/docs/akka/2.1.4/general/configuration.html "Akka Documentation on configuration")
.

Edit your application.conf from src/main/resources/application.conf

**application.conf**

```scala
    akka {
      loggers = ["akka.event.slf4j.Slf4jLogger"]
      loglevel = INFO
         }
    }
```
Then, your are done

Happy Coding

Examples
---
You will find some of them in kamon-example folder

1. git clone git://github.com/kamon/kamon.git

2. cd Kamon/kamon-examples

For akka, you will find akka-kamon-examples that shows you how to add a TraceContext and how to add a Console Reporter with the metrics.
