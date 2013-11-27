---
title: About Me
layout: default
---

NewRelic Module
===
A simple module to report some application metrics like External Services, Errors and Apdex. 

## Usage

### SBT settings 

Add the following sbt dependencies to your project settings:

```scala
libraryDependencies += "org.kamon" 				 %  "kamon-newrelic" % "0.1.0"
libraryDependencies += "com.newrelic.agent.java" %  "newrelic-api"   % "3.1.0"
```
### Example Configuration

```scala
-javaagent:/path-to-newrelic-agent.jar, -Dnewrelic.environment=production, -Dnewrelic.config.file=/path-to-newrelic.yml
```

### Screenshot

![newrelic](/assets/img/newrelicdifu2.png "Screenshot NewRelic")


## Limitations
* The first implementation only supports a subset of NewRelic metrics

## Licensing

NewRelic has [its own, separate licensing](http://newrelic.com/terms).


