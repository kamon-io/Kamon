/* =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

lazy val kamon = (project in file("."))
  .disablePlugins(AssemblyPlugin)
  .settings(noPublishing: _*)
  .settings(crossScalaVersions := Nil)
  .aggregate(core, instrumentation, reporters, bundle)


lazy val core = (project in file("core"))
  .disablePlugins(AssemblyPlugin)
  .settings(noPublishing: _*)
  .settings(crossScalaVersions := Nil)
  .aggregate(`kamon-core`, `kamon-status-page`, `kamon-testkit`, `kamon-core-tests`, `kamon-core-bench`)


lazy val `kamon-core` = (project in file("core/kamon-core"))
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(AssemblyPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](version),
    buildInfoPackage := "kamon.status",
    scalacOptions ++= { if(scalaBinaryVersion.value == "2.11") Seq("-Ydelambdafy:method") else Seq.empty },
    assemblyShadeRules in assembly := Seq(
      ShadeRule.rename("org.jctools.**"                             -> "kamon.lib.@0").inAll,
      ShadeRule.rename("org.HdrHistogram.Base*"                     -> "@0").inAll,
      ShadeRule.rename("org.HdrHistogram.HdrHistogramInternalState" -> "@0").inAll,
      ShadeRule.rename("org.HdrHistogram.ZigZag"                    -> "@0").inAll,
      ShadeRule.rename("org.HdrHistogram.*"                         -> "org.HdrHistogram.Shaded@1").inAll,
      ShadeRule.keep(
        "kamon.Kamon",
        "kamon.context.**",
        "kamon.metric.**",
        "kamon.module.**",
        "kamon.status.**",
        "kamon.tag.**",
        "kamon.trace.**",
        "kamon.util.**",
        "org.HdrHistogram.AtomicHistogram",
        "org.jctools.queues.MpscArrayQueue",
      ).inAll
    ),
    libraryDependencies ++= Seq(
      "com.typesafe"      %  "config"       % "1.3.1",
      "org.slf4j"         %  "slf4j-api"    % "1.7.25",
      "org.hdrhistogram"  %  "HdrHistogram" % "2.1.9" % "provided,shaded",
      "org.jctools"       %  "jctools-core" % "2.1.1" % "provided,shaded",
    ),
  )


lazy val `kamon-status-page` = (project in file("core/kamon-status-page"))
  .enablePlugins(AssemblyPlugin)
  .settings(
    assemblyShadeRules in assembly := Seq(
      ShadeRule.rename("com.grack.nanojson.**"  -> "kamon.lib.@0").inAll,
      ShadeRule.rename("fi.iki.elonen.**"       -> "kamon.lib.@0").inAll,
    ),
    libraryDependencies ++= Seq(
      "com.grack"     %  "nanojson"  % "1.1"   % "provided,shaded",
      "org.nanohttpd" %  "nanohttpd" % "2.3.1" % "provided,shaded"
    )
  ).dependsOn(`kamon-core`)


lazy val `kamon-testkit` = (project in file("core/kamon-testkit"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    libraryDependencies += scalatest % "test"
  ).dependsOn(`kamon-core`)


lazy val `kamon-core-tests` = (project in file("core/kamon-core-tests"))
  .disablePlugins(AssemblyPlugin)
  .settings(noPublishing: _*)
  .settings(
    libraryDependencies ++= Seq(
      scalatest % "test",
      logbackClassic % "test"
    )
  ).dependsOn(`kamon-testkit`)


lazy val `kamon-core-bench` = (project in file("core/kamon-core-bench"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JmhPlugin)
  .settings(noPublishing: _*)
  .dependsOn(`kamon-core`)


/**
  * Instrumentation Projects
  */

lazy val instrumentation = (project in file("instrumentation"))
  .disablePlugins(AssemblyPlugin)
  .settings(noPublishing: _*)
  .settings(crossScalaVersions := Nil)
  .aggregate(
    `kamon-instrumentation-common`,
    `kamon-executors`,
    `kamon-executors-bench`,
    `kamon-scala-future`,
    `kamon-twitter-future`,
    `kamon-scalaz-future`,
    `kamon-cats-io`,
    `kamon-logback`,
    `kamon-jdbc`,
    `kamon-kafka`,
    `kamon-mongo`,
    `kamon-cassandra`,
    `kamon-annotation`,
    `kamon-annotation-api`,
    `kamon-system-metrics`,
    `kamon-akka`,
    `kamon-akka-http`,
    `kamon-play`,
    `kamon-okhttp`,
  )


lazy val `kamon-instrumentation-common` = (project in file("instrumentation/kamon-instrumentation-common"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    resolvers += Resolver.mavenLocal,
    libraryDependencies ++= Seq(
      slf4jApi % "test",
      scalatest % "test",
      kanelaAgent % "provided"
    )
  ).dependsOn(`kamon-core`, `kamon-testkit` % "test")


lazy val `kamon-executors` = (project in file("instrumentation/kamon-executors"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      scalatest % "test",
      logbackClassic % "test",
      "com.google.guava" % "guava" % "24.1-jre" % "test",
    )
  ).dependsOn(`kamon-core`, `kamon-instrumentation-common`, `kamon-testkit` % "test")

lazy val `kamon-executors-bench` = (project in file("instrumentation/kamon-executors-bench"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JmhPlugin)
  .settings(noPublishing: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.google.guava" % "guava" % "24.1-jre",
      kanelaAgent % "provided",
    )
  ).dependsOn(`kamon-core`, `kamon-instrumentation-common`)


lazy val `kamon-twitter-future` = (project in file("instrumentation/kamon-twitter-future"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    bintrayPackage := "kamon-futures",
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      "com.twitter" %% "util-core" % "20.3.0" % "provided",
      scalatest % "test",
      logbackClassic % "test"
    )
  ).dependsOn(`kamon-core`, `kamon-executors`, `kamon-testkit` % "test")


lazy val `kamon-scalaz-future` = (project in file("instrumentation/kamon-scalaz-future"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    bintrayPackage := "kamon-futures",
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      "org.scalaz" %% "scalaz-concurrent" % "7.2.28" % "provided",
      scalatest % "test",
      logbackClassic % "test"
    )
  ).dependsOn(`kamon-core`, `kamon-executors`, `kamon-testkit` % "test")


lazy val `kamon-scala-future` = (project in file("instrumentation/kamon-scala-future"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    bintrayPackage := "kamon-futures",
    libraryDependencies ++=Seq(
      kanelaAgent % "provided",
      scalatest % "test",
      logbackClassic % "test"
    )
  ).dependsOn(`kamon-core`, `kamon-executors`, `kamon-testkit` % "test")


lazy val `kamon-cats-io` = (project in file("instrumentation/kamon-cats-io"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    bintrayPackage := "kamon-futures",
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      {
        if(scalaBinaryVersion.value == "2.11")
          "org.typelevel" %% "cats-effect" % "2.0.0" % "provided"
        else
          "org.typelevel" %% "cats-effect" % "2.1.2" % "provided"
      },
      scalatest % "test",
      logbackClassic % "test"
    ),

  ).dependsOn(`kamon-core`, `kamon-executors`, `kamon-testkit` % "test")


lazy val `kamon-logback` = (project in file("instrumentation/kamon-logback"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      logbackClassic % "provided",
      scalatest % "test",
    )
  ).dependsOn(`kamon-core`, `kamon-instrumentation-common`, `kamon-testkit` % "test")


lazy val `kamon-jdbc` = (project in file("instrumentation/kamon-jdbc"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      "com.zaxxer"                % "HikariCP"                  % "2.6.2" % "provided",
      "org.mariadb.jdbc"          % "mariadb-java-client"       % "2.2.6" % "provided",
      "com.typesafe.slick"       %% "slick"                     % "3.3.2" % "provided",
      "org.postgresql"            % "postgresql"                % "42.2.5" % "provided",

      scalatest % "test",
      logbackClassic % "test",
      "com.typesafe.slick"       %% "slick-hikaricp"            % "3.3.2" % "test",
      "com.h2database"            % "h2"                        % "1.4.182" % "test",
      "org.xerial"                % "sqlite-jdbc"               % "3.27.2.1" % "test",
      "ch.vorburger.mariaDB4j"    % "mariaDB4j"                 % "2.4.0" % "test"
    )
  ).dependsOn(`kamon-core`, `kamon-executors`, `kamon-testkit` % "test")


lazy val `kamon-kafka` = (project in file("instrumentation/kamon-kafka"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    libraryDependencies ++= Seq(
      kanelaAgent                 % "provided",
      "org.apache.kafka"          % "kafka-clients"     % "2.4.0" % "provided",

      scalatest                   % "test",
      logbackClassic              % "test",
      "io.github.embeddedkafka"   %% "embedded-kafka"   % "2.4.1.1" % "test"
    )
  ).dependsOn(`kamon-core`, `kamon-executors`, `kamon-testkit` % "test")




lazy val `kamon-mongo` = (project in file("instrumentation/kamon-mongo"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      "org.mongodb"         %   "mongodb-driver-sync"             % "3.11.0" % "provided",
      "org.mongodb.scala"   %%  "mongo-scala-driver"              % "2.7.0" % "provided",
      "org.mongodb"         %   "mongodb-driver-reactivestreams"  % "1.12.0" % "provided",

      scalatest % "test",
      logbackClassic % "test",
      "de.flapdoodle.embed" %   "de.flapdoodle.embed.mongo"       % "2.2.0" % "test"
    )
  ).dependsOn(`kamon-core`, `kamon-instrumentation-common`, `kamon-testkit` % "test")

lazy val `kamon-cassandra` = (project in file("instrumentation/kamon-cassandra"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      "com.datastax.cassandra" % "cassandra-driver-core" % "3.6.0" % "provided",
      "org.apache.cassandra"   % "cassandra-all"         % "3.11.2" % "provided",
      scalatest % "test",
      logbackClassic % "test",
      "org.cassandraunit"      % "cassandra-unit"        % "3.11.2.0" % "test"
    )
  ).dependsOn(`kamon-core`, `kamon-instrumentation-common`, `kamon-testkit` % "test", `kamon-executors`)



lazy val `kamon-annotation-api` = (project in file("instrumentation/kamon-annotation-api"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    crossPaths := false,
    autoScalaLibrary := false,
    publishMavenStyle := true,
    javacOptions in (Compile, doc) := Seq("-Xdoclint:none")
  )


lazy val `kamon-annotation` = (project in file("instrumentation/kamon-annotation"))
  .enablePlugins(JavaAgent)
  .enablePlugins(AssemblyPlugin)
  .settings(instrumentationSettings: _*)
  .settings(
    assemblyShadeRules in assembly := Seq(
      ShadeRule.rename("javax.el.**"    -> "kamon.lib.@0").inAll,
      ShadeRule.rename("com.sun.el.**"  -> "kamon.lib.@0").inAll,
      ShadeRule.rename("com.github.ben-manes.**"  -> "kamon.lib.@0").inAll,
    ),
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      "com.github.ben-manes.caffeine" % "caffeine" % "2.8.5" % "provided,shaded", // provided? no?
      "org.glassfish" % "javax.el" % "3.0.1-b11" % "provided,shaded",
      scalatest % "test",
      logbackClassic % "test",
    )
  ).dependsOn(`kamon-core`, `kamon-annotation-api`, `kamon-testkit` % "test")


lazy val `kamon-system-metrics` = (project in file("instrumentation/kamon-system-metrics"))
  .disablePlugins(AssemblyPlugin)
  .settings(instrumentationSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.github.oshi" % "oshi-core" % "4.2.1",
      scalatest % "test",
      logbackClassic % "test"
    )
  ).dependsOn(`kamon-core`)


lazy val `kamon-akka` = (project in file("instrumentation/kamon-akka"))
  .enablePlugins(JavaAgent)
  .disablePlugins(AssemblyPlugin)
  .settings(instrumentationSettings: _*)
  .dependsOn(
    `kamon-scala-future` % "compile,common,akka-2.5,akka-2.6",
    `kamon-testkit` % "test,test-common,test-akka-2.5,test-akka-2.6"
  )


lazy val `kamon-akka-http` = (project in file("instrumentation/kamon-akka-http"))
  .enablePlugins(JavaAgent)
  .disablePlugins(AssemblyPlugin)
  .settings(instrumentationSettings)
  .settings(Seq(
    resolvers += Resolver.bintrayRepo("hseeberger", "maven"),
    javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.10" % "test",
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      "com.typesafe.akka" %% "akka-http"            % "10.1.12" % "provided",
      "com.typesafe.akka" %% "akka-http2-support"   % "10.1.12" % "provided",
      "com.typesafe.akka" %% "akka-stream"          % "2.5.31" % "provided",

      scalatest % "test",
      slf4jApi % "test",
      slf4jnop % "test",
      okHttp % "test",
      "com.typesafe.akka" %% "akka-http-testkit"    % "10.1.12" % "test",
      "de.heikoseeberger" %% "akka-http-json4s"     % "1.27.0" % "test",
      "org.json4s"        %% "json4s-native"        % "3.6.7" % "test",
    )
  )).dependsOn(`kamon-akka`, `kamon-testkit` % "test")


lazy val `kamon-play` = (project in file("instrumentation/kamon-play"))
  .enablePlugins(JavaAgent)
  .disablePlugins(AssemblyPlugin)
  .settings(instrumentationSettings)
  .dependsOn(
    `kamon-akka` % "compile,test-common,test-play-2.8,test-play-2.7,test-play-2.6",
    `kamon-akka-http` % "compile,test-common,test-play-2.8,test-play-2.7,test-play-2.6",
    `kamon-testkit` % "test-common,test-play-2.8,test-play-2.7,test-play-2.6"
  )


lazy val `kamon-okhttp` = (project in file("instrumentation/kamon-okhttp"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      "com.squareup.okhttp3"      % "okhttp"                    % "3.14.9" % "provided",

      scalatest % "test",
      logbackClassic % "test",
      "org.eclipse.jetty"         % "jetty-server"              % "9.4.25.v20191220" % "test",
      "org.eclipse.jetty"         % "jetty-servlet"             % "9.4.25.v20191220" % "test",
    )
  ).dependsOn(`kamon-core`, `kamon-executors`, `kamon-testkit` % "test")


/**
  * Reporters
  */

lazy val reporters = (project in file("reporters"))
  .disablePlugins(AssemblyPlugin)
  .settings(noPublishing: _*)
  .settings(crossScalaVersions := Nil)
  .aggregate(
    `kamon-apm-reporter`,
    `kamon-datadog`,
    `kamon-graphite`,
    `kamon-influxdb`,
    `kamon-jaeger`,
    `kamon-newrelic`,
    `kamon-prometheus`,
    `kamon-statsd`,
    `kamon-zipkin`,
  )


lazy val `kamon-datadog` = (project in file("reporters/kamon-datadog"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    libraryDependencies ++= Seq(
      okHttp,
      "com.typesafe.play" %% "play-json" % "2.7.4",

      scalatest % "test",
      slf4jApi % "test",
      slf4jnop % "test",
      okHttpMockServer % "test"
    )

  ).dependsOn(`kamon-core`, `kamon-testkit` % "test")


lazy val `kamon-apm-reporter` = (project in file("reporters/kamon-apm-reporter"))
  .enablePlugins(AssemblyPlugin)
  .settings(
    test in assembly := {},
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", xs @ _*)  => MergeStrategy.discard
      case _                              => MergeStrategy.first
    },
    assemblyShadeRules in assembly := Seq(
       ShadeRule.rename("fastparse.**"              -> "kamon.apm.shaded.@0").inAll
      ,ShadeRule.rename("fansi.**"                  -> "kamon.apm.shaded.@0").inAll
      ,ShadeRule.rename("sourcecode.**"             -> "kamon.apm.shaded.@0").inAll
      ,ShadeRule.rename("com.google.protobuf.**"    -> "kamon.apm.shaded.@0").inAll
      ,ShadeRule.rename("google.protobuf.**"        -> "kamon.apm.shaded.@0").inAll
      ,ShadeRule.rename("okhttp3.**"                -> "kamon.apm.shaded.@0").inAll
      ,ShadeRule.rename("okio.**"                   -> "kamon.apm.shaded.@0").inAll
    ),

    libraryDependencies ++= Seq(
      scalatest % "test",
      okHttp % "provided,shaded",
      "com.google.protobuf"   % "protobuf-java" % "3.8.0" % "provided,shaded",

      "ch.qos.logback"    %  "logback-classic"  % "1.2.3" % "test",
      "org.scalatest"     %% "scalatest"        % "3.0.8" % "test",
      "com.typesafe.akka" %% "akka-http"        % "10.1.8" % "test",
      "com.typesafe.akka" %% "akka-stream"      % "2.5.23" % "test",
      "com.typesafe.akka" %% "akka-testkit"     % "2.5.23" % "test"
    )
  ).dependsOn(`kamon-core`, `kamon-testkit` % "test")


lazy val `kamon-statsd` = (project in file("reporters/kamon-statsd"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    libraryDependencies += scalatest % Test,
    parallelExecution in Test := false
  ).dependsOn(`kamon-core`)


lazy val `kamon-zipkin` = (project in file("reporters/kamon-zipkin"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    parallelExecution in Test := false,
    libraryDependencies ++= Seq(
      "io.zipkin.reporter2" % "zipkin-reporter" % "2.7.15",
      "io.zipkin.reporter2" % "zipkin-sender-okhttp3" % "2.7.15",
      scalatest % "test"
    )
  ).dependsOn(`kamon-core`)


lazy val `kamon-jaeger` = (project in file("reporters/kamon-jaeger"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    parallelExecution in Test := false,
    libraryDependencies ++= Seq(
      "io.jaegertracing" % "jaeger-thrift" % "1.1.0",
      scalatest % "test"
    )
  ).dependsOn(`kamon-core`)


lazy val `kamon-influxdb` = (project in file("reporters/kamon-influxdb"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    libraryDependencies ++= Seq(
      okHttp,
      okHttpMockServer % "test",
      scalatest % "test"
    )
  ).dependsOn(`kamon-core`, `kamon-testkit` % "test")


lazy val `kamon-graphite` = (project in file("reporters/kamon-graphite"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    libraryDependencies ++= Seq(
      scalatest % "test",
      logbackClassic % "test"
    )
  ).dependsOn(`kamon-core`, `kamon-testkit` % "test")


lazy val `kamon-newrelic` = (project in file("reporters/kamon-newrelic"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "com.newrelic.telemetry" % "telemetry" % "0.6.1",
      "com.newrelic.telemetry" % "telemetry-http-okhttp" % "0.6.1",
      scalatest % "test",
      "org.mockito" % "mockito-core" % "3.1.0" % "test"
    )
  ).dependsOn(`kamon-core`)


lazy val `kamon-prometheus` = (project in file("reporters/kamon-prometheus"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    libraryDependencies ++= Seq(
      okHttp,
      scalatest % "test",
      logbackClassic % "test"
    )
  ).dependsOn(`kamon-core`, `kamon-testkit` % "test")


lazy val bundle = (project in file("bundle"))
  .disablePlugins(AssemblyPlugin)
  .settings(noPublishing: _*)
  .settings(crossScalaVersions := Nil)
  .aggregate(
    `kamon-bundle`
  )


import com.lightbend.sbt.javaagent.Modules
import BundleKeys._

val `kamon-bundle` = (project in file("bundle/kamon-bundle"))
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(AssemblyPlugin)
  .settings(
    moduleName := "kamon-bundle",
    kanelaAgentVersion := "1.0.6",
    buildInfoPackage := "kamon.bundle",
    buildInfoKeys := Seq[BuildInfoKey](kanelaAgentJarName),
    kanelaAgentJar := update.value.matching(Modules.exactFilter(kanelaAgent)).head,
    kanelaAgentJarName := kanelaAgentJar.value.getName,
    resourceGenerators in Compile += Def.task(Seq(kanelaAgentJar.value)).taskValue,
    libraryDependencies ++= Seq(
      "com.github.oshi" % "oshi-core" % "4.2.1",
      kanelaAgent % "provided",
      "net.bytebuddy" % "byte-buddy-agent" % "1.9.12" % "provided,shaded",
    ),
    fullClasspath in assembly ++= (internalDependencyClasspath in Shaded).value,
    assemblyShadeRules in assembly := Seq(
      ShadeRule.zap("**module-info").inAll,
      ShadeRule.rename("net.bytebuddy.agent.**" -> "kamon.lib.@0").inAll
    ),
    assemblyExcludedJars in assembly := {
      (fullClasspath in assembly).value.filter(f => {
        val fileName = f.data.getName()
        fileName.contains("kamon-core") || fileName.contains("oshi-core")
      })
    },
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(
      includeBin = true,
      includeScala = false,
      includeDependency = false,
      cacheOutput = false
    ),
    assemblyMergeStrategy in assembly := {
      case "reference.conf" => MergeStrategy.concat
      case anyOther         => (assemblyMergeStrategy in assembly).value(anyOther)
    }
  ).dependsOn(
    `kamon-core`,
    `kamon-status-page` % "shaded",
    `kamon-instrumentation-common` % "shaded",
    `kamon-executors` % "shaded",
    `kamon-scala-future` % "shaded",
    `kamon-twitter-future` % "shaded",
    `kamon-scalaz-future` % "shaded",
    `kamon-cats-io` % "shaded",
    `kamon-logback` % "shaded",
    `kamon-jdbc` % "shaded",
    `kamon-kafka` % "shaded",
    `kamon-mongo` % "shaded",
    `kamon-cassandra` % "shaded",
    `kamon-annotation` % "shaded",
    `kamon-annotation-api` % "shaded",
    `kamon-system-metrics` % "shaded",
    `kamon-akka` % "shaded",
    `kamon-akka-http` % "shaded",
    `kamon-play` % "shaded",
    `kamon-okhttp` % "shaded",
  )
