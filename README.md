# New Relic integration

This repository provides an integration with the [New Relic](https://newrelic.com) platform.
It provides two reporters capable of sending trace spans and dimensional 
metrics to New Relic data ingest APIs. 

# Install

In order to use the New Relic reporters in your application, you must first
add the `kamon-newrelic` reporter dependency to your `build.sbt`:

```
libraryDependencies ++= Seq(
    "io.kamon" %% "kamon-bundle" % "2.0.2",
    "com.newrelic.telemetry" %% "kamon-newrelic-reporter" % "0.0.3",
    ...
)
```

Once the dependency is included in your project, Kamon will automatically
register the reporters.

## Configuration

In order for the reporters to send data, they require a [New Relic Insights API key](https://docs.newrelic.com/docs/apis/get-started/intro-apis/types-new-relic-api-keys#insert-key-create).  
You should populate an environment variable called `INSIGHTS_INSERT_KEY` with your
API key, and configure it in your `application.conf` like this:

```hocon
kamon.newrelic {
    # A New Relic Insights API Insert Key is required to send trace data to New Relic
    # https://docs.newrelic.com/docs/apis/get-started/intro-apis/types-new-relic-api-keys#insert-key-create
    nr-insights-insert-key = ${?INSIGHTS_INSERT_KEY}
}
```

If you can't use an environment variable, you can replace the above with the actual key like  
`nr-insights-insert-key = abc123secretvalue123`. 

**Note**: This is less secure and additional precautions must be taken (like considering file
permissions and excluding from source control).

# Find and use your data

For a detailed explanation of what data is exported and how it appears inside the New Relic platform, please 
see the documentation in [the New Relic exporter specs repo](https://github.com/newrelic/newrelic-exporter-specs/tree/master/kamon).

Tips on how to find and query your data in New Relic:
- [Find metric data](https://docs.newrelic.com/docs/data-ingest-apis/get-data-new-relic/metric-api/introduction-metric-api#find-data)
- [Find trace/span data](https://docs.newrelic.com/docs/understand-dependencies/distributed-tracing/trace-api/introduction-trace-api#view-data)

For general querying information, see:
- [Query New Relic data](https://docs.newrelic.com/docs/using-new-relic/data/understand-data/query-new-relic-data)
- [Intro to NRQL](https://docs.newrelic.com/docs/query-data/nrql-new-relic-query-language/getting-started/nrql-syntax-clauses-functions)

# Development

Great!  You have cloned this repository and want to build it in order to test 
things out, experiment, and make changes...awesome!

First, make sure that you have a working installation of scala 2.13.0 and
sbt. Then you can run:

```
$ sbt compile  
```

If you are developing locally, you may also be interested in:

* `sbt publishLocal` - (publish to [local Ivy repo](https://www.scala-sbt.org/1.x/docs/Publishing.html#Publishing+locally))
* `sbt publishM2` - (publish to [local Maven repo](https://www.scala-sbt.org/1.x/docs/Publishing.html#Publishing+locally))
* `sbt package` - (build a local jar, output in `kamon-newrelic/target/scala-2.13/`)


# Contributing

Please see [CONTRIBUTING.md](CONTRIBUTING.md) for information on helping out 
with this project.
