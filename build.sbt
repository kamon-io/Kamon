/* =========================================================================================
 * Copyright © 2013-2021 the kamon project <http://kamon.io/>
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
  .aggregate(core, instrumentation, reporters, bundle, `bill-of-materials`)

val coreProjects = Seq[ProjectReference](
  `kamon-core`, `kamon-status-page`, `kamon-testkit`, `kamon-core-tests`, `kamon-core-bench`
)

lazy val core = (project in file("core"))
  .disablePlugins(AssemblyPlugin)
  .settings(noPublishing: _*)
  .settings(crossScalaVersions := Nil)
  .aggregate(coreProjects: _*)


lazy val `kamon-core` = (project in file("core/kamon-core"))
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(AssemblyPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](version),
    buildInfoPackage := "kamon.status",
    crossScalaVersions += scala3Version,
    scalacOptions ++= { if(scalaBinaryVersion.value == "2.11") Seq("-Ydelambdafy:method") else Seq.empty },
    assembly / assemblyShadeRules := Seq(
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
        "kamon.svm.**",
        "org.HdrHistogram.AtomicHistogram",
        "org.jctools.queues.MpscArrayQueue",
      ).inAll
    ),
    libraryDependencies ++= Seq(
      "com.typesafe"      %  "config"       % "1.4.1",
      "org.slf4j"         %  "slf4j-api"    % "1.7.25",
      "org.hdrhistogram"  %  "HdrHistogram" % "2.1.9" % "provided,shaded",
      "org.jctools"       %  "jctools-core" % "3.3.0" % "provided,shaded",
      "com.oracle.substratevm" % "svm"      % "19.2.1" % "provided"
    ),
  )


lazy val `kamon-status-page` = (project in file("core/kamon-status-page"))
  .enablePlugins(AssemblyPlugin)
  .settings(
    crossScalaVersions += scala3Version,
    assembly / assemblyShadeRules := Seq(
      ShadeRule.rename("com.grack.nanojson.**"  -> "kamon.lib.@0").inAll,
      ShadeRule.rename("fi.iki.elonen.**"       -> "kamon.lib.@0").inAll,
    ),
    libraryDependencies ++= Seq(
      "com.grack"     %  "nanojson"  % "1.6"   % "provided,shaded",
      "org.nanohttpd" %  "nanohttpd" % "2.3.1" % "provided,shaded"
    )
  ).dependsOn(`kamon-core`)


lazy val `kamon-testkit` = (project in file("core/kamon-testkit"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    crossScalaVersions += scala3Version,
    libraryDependencies += scalatest % "provided,test"
  ).dependsOn(`kamon-core`)


lazy val `kamon-core-tests` = (project in file("core/kamon-core-tests"))
  .disablePlugins(AssemblyPlugin)
  .settings(noPublishing: _*)
  .settings(
    crossScalaVersions += scala3Version,
    libraryDependencies ++= Seq(
      scalatest % "test",
      logbackClassic % "test"
    )
  ).dependsOn(`kamon-testkit`)


lazy val `kamon-core-bench` = (project in file("core/kamon-core-bench"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JmhPlugin)
  .settings(crossScalaVersions += scala3Version)
  .settings(noPublishing: _*)
  .dependsOn(`kamon-core`)


/**
 * Instrumentation Projects
 */
val instrumentationProjects = Seq[ProjectReference](
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
  `kamon-mongo-legacy`,
  `kamon-cassandra`,
  `kamon-elasticsearch`,
  `kamon-spring`,
  `kamon-annotation`,
  `kamon-annotation-api`,
  `kamon-system-metrics`,
  `kamon-akka`,
  `kamon-akka-http`,
  `kamon-play`,
  `kamon-okhttp`,
  `kamon-tapir`,
  `kamon-redis`,
  `kamon-caffeine`,
)

lazy val instrumentation = (project in file("instrumentation"))
  .disablePlugins(AssemblyPlugin)
  .settings(noPublishing: _*)
  .settings(crossScalaVersions := Nil)
  .aggregate(instrumentationProjects: _*)


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
      jsqlparser,
      kanelaAgent % "provided",
      "com.zaxxer"                % "HikariCP"                  % "4.0.3" % "provided",
      "org.mariadb.jdbc"          % "mariadb-java-client"       % "2.2.6" % "provided",
      "com.typesafe.slick"       %% "slick"                     % "3.3.2" % "provided",
      "org.postgresql"            % "postgresql"                % "42.2.5" % "provided",

      scalatest % "test",
      logbackClassic % "test",
      "com.typesafe.slick"       %% "slick-hikaricp"            % "3.3.2" % "test",
      "com.h2database"            % "h2"                        % "1.4.182" % "test",
      "org.xerial"                % "sqlite-jdbc"               % "3.34.0" % "test",
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


lazy val `kamon-mongo-legacy` = (project in file("instrumentation/kamon-mongo-legacy"))
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


lazy val `kamon-mongo` = (project in file("instrumentation/kamon-mongo"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      "org.mongodb"         %   "mongodb-driver-sync"             % "4.2.3" % "provided",
      "org.mongodb.scala"   %%  "mongo-scala-driver"              % "4.2.3" % "provided",
      "org.mongodb"         %   "mongodb-driver-reactivestreams"  % "4.2.3" % "provided",

      scalatest % "test",
      logbackClassic % "test",
      "de.flapdoodle.embed" %   "de.flapdoodle.embed.mongo"       % "2.2.0" % "test"
    )
  ).dependsOn(`kamon-core`, `kamon-instrumentation-common`, `kamon-testkit` % "test")

lazy val `kamon-cassandra` = (project in file("instrumentation/kamon-cassandra"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .dependsOn(`kamon-core`, `kamon-instrumentation-common`, `kamon-testkit` % "test", `kamon-executors`)

lazy val `kamon-elasticsearch` = (project in file("instrumentation/kamon-elasticsearch"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    Test / run / fork := true,
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      "org.elasticsearch.client" % "elasticsearch-rest-client" % "7.9.1" % "provided",
      "org.elasticsearch.client" % "elasticsearch-rest-high-level-client" % "7.9.1" % "provided",
      scalatest % "test",
      logbackClassic % "test",
      "com.dimafeng" %% "testcontainers-scala" % "0.39.3" % "test",
      "com.dimafeng" %% "testcontainers-scala-elasticsearch" % "0.39.3" % "test"
    )
  ).dependsOn(`kamon-core`, `kamon-instrumentation-common`, `kamon-testkit` % "test")

lazy val `kamon-spring` = (project in file("instrumentation/kamon-spring"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    Test / run / fork := true,
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      "org.springframework.boot" % "spring-boot-starter-web" % "2.4.2" % "provided",
      "org.springframework.boot" % "spring-boot-starter-webflux" % "2.4.2" % "provided",

      okHttp % "test",
      "com.h2database" % "h2" % "1.4.200" % "test",
      "org.springframework.boot" % "spring-boot-starter-data-jpa" % "2.4.2" % "test",
      scalatest % "test",
      logbackClassic % "test",
    )
  ).dependsOn(`kamon-core`, `kamon-instrumentation-common`, `kamon-testkit` % "test")


lazy val `kamon-annotation-api` = (project in file("instrumentation/kamon-annotation-api"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    crossPaths := false,
    autoScalaLibrary := false,
    publishMavenStyle := true,
    Compile / doc / javacOptions := Seq("-Xdoclint:none")
  )


lazy val `kamon-annotation` = (project in file("instrumentation/kamon-annotation"))
  .enablePlugins(JavaAgent)
  .enablePlugins(AssemblyPlugin)
  .settings(instrumentationSettings: _*)
  .settings(
    assembly / assemblyShadeRules := Seq(
      ShadeRule.rename("javax.el.**"    -> "kamon.lib.@0").inAll,
      ShadeRule.rename("com.sun.el.**"  -> "kamon.lib.@0").inAll,
      ShadeRule.rename("com.github.benmanes.**"  -> "kamon.lib.@0").inAll,
      ShadeRule.rename("com.google.errorprone.**"  -> "kamon.lib.@0").inAll,
      ShadeRule.rename("org.checkerframework.**"  -> "kamon.lib.@0").inAll,
    ),
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      "com.github.ben-manes.caffeine" % "caffeine" % "2.8.5" % "provided,shaded",
      "org.glassfish" % "javax.el" % "3.0.1-b11" % "provided,shaded",
      scalatest % "test",
      logbackClassic % "test",
    )
  ).dependsOn(`kamon-core`, `kamon-annotation-api`, `kamon-testkit` % "test")


lazy val `kamon-system-metrics` = (project in file("instrumentation/kamon-system-metrics"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    libraryDependencies ++= Seq(
      oshiCore,
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


def akkaHttpVersion(scalaVersion: String) = scalaVersion match {
  case "2.11" => "10.1.12"
  case _      => "10.2.3"
}

lazy val `kamon-akka-http` = (project in file("instrumentation/kamon-akka-http"))
  .enablePlugins(JavaAgent)
  .disablePlugins(AssemblyPlugin)
  .settings(instrumentationSettings)
  .settings(Seq(
    resolvers += Resolver.bintrayRepo("hseeberger", "maven"),
    javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.10" % "test",
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion(scalaBinaryVersion.value) % "provided",
      "com.typesafe.akka" %% "akka-http2-support"   % akkaHttpVersion(scalaBinaryVersion.value)  % "provided",
      "com.typesafe.akka" %% "akka-stream"          % "2.5.32" % "provided",

      scalatest % "test",
      slf4jApi % "test",
      slf4jnop % "test",
      okHttp % "test",
      "com.typesafe.akka" %% "akka-http-testkit"    % akkaHttpVersion(scalaBinaryVersion.value) % "test",
      "de.heikoseeberger" %% "akka-http-json4s"     % "1.27.0" % "test",
      "org.json4s"        %% "json4s-native"        % "3.6.7" % "test",
    ),
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

lazy val `kamon-tapir` = (project in file("instrumentation/kamon-tapir"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(
    instrumentationSettings,
    crossScalaVersions := Seq("2.12.11", "2.13.1"),
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      "com.softwaremill.sttp.tapir" %% "tapir-core" % "0.17.9" % "provided",
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion(scalaBinaryVersion.value) % "provided",

      "com.softwaremill.sttp.tapir" %% "tapir-akka-http-server" % "0.17.9" % "test",
      "com.softwaremill.sttp.client3" %% "core" % "3.3.0-RC2" % "test",
      scalatest % "test",
      logbackClassic % "test",
    )
  ).dependsOn(`kamon-core`, `kamon-akka-http`, `kamon-testkit` % "test")

lazy val `kamon-redis` = (project in file("instrumentation/kamon-redis"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      "redis.clients" % "jedis"  % "3.6.0" % "provided",
      "io.lettuce" % "lettuce-core"  % "6.1.2.RELEASE" % "provided",
      "com.github.etaty" %% "rediscala" % "1.9.0" % "provided",

      scalatest % "test",
      logbackClassic % "test",
      "org.testcontainers" % "testcontainers" % "1.15.3" % "test",
    )
  ).dependsOn(`kamon-core`, `kamon-testkit` % "test")

lazy val `kamon-caffeine` = (project in file("instrumentation/kamon-caffeine"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      "com.github.ben-manes.caffeine" % "caffeine" % "2.8.5" % "provided",

      scalatest % "test",
      logbackClassic % "test",
    )
  ).dependsOn(`kamon-core`, `kamon-testkit` % "test")

/**
 * Reporters
 */
val reportersProjects = Seq[ProjectReference](
  `kamon-apm-reporter`,
  `kamon-datadog`,
  `kamon-graphite`,
  `kamon-influxdb`,
  `kamon-jaeger`,
  `kamon-newrelic`,
  `kamon-opentelemetry`,
  `kamon-prometheus`,
  `kamon-statsd`,
  `kamon-zipkin`,
)

lazy val reporters = (project in file("reporters"))
  .disablePlugins(AssemblyPlugin)
  .settings(noPublishing: _*)
  .settings(crossScalaVersions := Nil)
  .aggregate(reportersProjects: _*)


lazy val `kamon-datadog` = (project in file("reporters/kamon-datadog"))
  .enablePlugins(AssemblyPlugin)
  .settings(
    crossScalaVersions += scala3Version,
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", xs @ _*)  => MergeStrategy.discard
      case _                              => MergeStrategy.first
    },
    assembly / assemblyShadeRules := Seq(
      ShadeRule.rename("okhttp3.**"                -> "kamon.lib.@1").inAll,
      ShadeRule.rename("okio.**"                   -> "kamon.lib.@1").inAll
    ),
    libraryDependencies ++= Seq(
      okHttp % "shaded,provided",
      "com.grack" % "nanojson" % "1.6",

      ("com.typesafe.play" %% "play-json" % "2.7.4" % "test").cross(CrossVersion.for3Use2_13),
      scalatest % "test",
      slf4jApi % "test",
      slf4jnop % "test",
      okHttpMockServer % "test"
    )
  ).dependsOn(`kamon-core`, `kamon-testkit` % "test")


lazy val `kamon-apm-reporter` = (project in file("reporters/kamon-apm-reporter"))
  .enablePlugins(AssemblyPlugin)
  .settings(
    crossScalaVersions += scala3Version,
    assembly / test := {},
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", xs @ _*)  => MergeStrategy.discard
      case _                              => MergeStrategy.first
    },
    assembly / assemblyShadeRules := Seq(
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
      "org.scalatest"     %% "scalatest"        % "3.2.9" % "test",
      ("com.typesafe.akka" %% "akka-http"        % "10.1.8" % "test").cross(CrossVersion.for3Use2_13),
      ("com.typesafe.akka" %% "akka-stream"      % "2.5.23" % "test").cross(CrossVersion.for3Use2_13),
      ("com.typesafe.akka" %% "akka-testkit"     % "2.5.23" % "test").cross(CrossVersion.for3Use2_13)
    )
  ).dependsOn(`kamon-core`, `kamon-testkit` % "test")


lazy val `kamon-statsd` = (project in file("reporters/kamon-statsd"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    crossScalaVersions += scala3Version,
    libraryDependencies += scalatest % Test,
  ).dependsOn(`kamon-core`)


lazy val `kamon-zipkin` = (project in file("reporters/kamon-zipkin"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    crossScalaVersions += scala3Version,
    libraryDependencies ++= Seq(
      "io.zipkin.reporter2" % "zipkin-reporter" % "2.7.15",
      "io.zipkin.reporter2" % "zipkin-sender-okhttp3" % "2.7.15",
      scalatest % "test"
    )
  ).dependsOn(`kamon-core`)


lazy val `kamon-jaeger` = (project in file("reporters/kamon-jaeger"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    crossScalaVersions += scala3Version,
    libraryDependencies ++= Seq(
      "io.jaegertracing" % "jaeger-thrift" % "1.1.0",
      scalatest % "test"
    )
  ).dependsOn(`kamon-core`)


lazy val `kamon-influxdb` = (project in file("reporters/kamon-influxdb"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    crossScalaVersions += scala3Version,
    libraryDependencies ++= Seq(
      okHttp,
      okHttpMockServer % "test",
      scalatest % "test"
    )
  ).dependsOn(`kamon-core`, `kamon-testkit` % "test")


lazy val `kamon-graphite` = (project in file("reporters/kamon-graphite"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    crossScalaVersions += scala3Version,
    libraryDependencies ++= Seq(
      scalatest % "test",
      logbackClassic % "test"
    )
  ).dependsOn(`kamon-core`, `kamon-testkit` % "test")


lazy val `kamon-newrelic` = (project in file("reporters/kamon-newrelic"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    crossScalaVersions += scala3Version,
    libraryDependencies ++= Seq(
      "com.newrelic.telemetry" % "telemetry-core" % "0.12.0",
      "com.newrelic.telemetry" % "telemetry-http-okhttp" % "0.12.0",
      scalatest % "test",
      "org.mockito" % "mockito-core" % "3.12.4" % "test"
    )
  ).dependsOn(`kamon-core`)

lazy val `kamon-opentelemetry` = (project in file("reporters/kamon-opentelemetry"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    crossScalaVersions += scala3Version,
    libraryDependencies ++= Seq(
      "io.opentelemetry" % "opentelemetry-proto" % "0.17.1",
      "io.grpc" % "grpc-netty" % "1.36.0",

      scalatest % "test",
      logbackClassic % "test"
    )
  ).dependsOn(`kamon-core`, `kamon-testkit` % "test")

lazy val `kamon-prometheus` = (project in file("reporters/kamon-prometheus"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    crossScalaVersions += scala3Version,
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
    buildInfoPackage := "kamon.bundle",
    buildInfoKeys := Seq[BuildInfoKey](kanelaAgentJarName),
    kanelaAgentJar := update.value.matching(Modules.exactFilter(kanelaAgent)).head,
    kanelaAgentJarName := kanelaAgentJar.value.getName,
    Compile / resourceGenerators += Def.task(Seq(kanelaAgentJar.value)).taskValue,
    assembly / fullClasspath ++= (Shaded / internalDependencyClasspath).value,
    assembly / assemblyShadeRules := Seq(
      ShadeRule.zap("**module-info").inAll,
      ShadeRule.rename("net.bytebuddy.agent.**" -> "kamon.lib.@0").inAll
    ),
    assembly / assemblyExcludedJars := {
      (assembly / fullClasspath).value.filter(f => {
        val fileName = f.data.getName()
        fileName.contains("kamon-core") || fileName.contains("oshi-core")
      })
    },
    assembly / assemblyOption := (assembly / assemblyOption).value.copy(
      includeBin = true,
      includeScala = false,
      includeDependency = false,
      cacheOutput = false
    ),
    assembly / assemblyMergeStrategy := {
      case "reference.conf" => MergeStrategy.concat
      case anyOther         => (assembly / assemblyMergeStrategy).value(anyOther)
    },
    libraryDependencies ++= Seq(
      oshiCore,
      kanelaAgent % "provided",
      "net.bytebuddy" % "byte-buddy-agent" % "1.9.12" % "provided,shaded",
    ),
    libraryDependencies ++= {
      // We are adding the Tapir instrumentation as library dependency (instead as a project dependency
      // via .dependsOn(...) as all other projects below) because it is not published for Scala 2.11 and
      // any proper solution requires major changes in the build. We might need to start using
      // sbt-projectmatrix in the future.
      //
      // Since the bundle dependencies are only important when publishing we can force a run of
      // publishLocal to get Tapir deployed on the local Ivy and then publish on the bundle as usual.
      if(scalaBinaryVersion.value == "2.11") Seq.empty else Seq(
        "io.kamon" %% "kamon-tapir" % version.value % "shaded"
      )
    },
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
    `kamon-mongo-legacy` % "shaded",
    `kamon-cassandra` % "shaded",
    `kamon-elasticsearch` % "shaded",
    `kamon-spring` % "shaded",
    `kamon-annotation` % "shaded",
    `kamon-annotation-api` % "shaded",
    `kamon-system-metrics` % "shaded",
    `kamon-akka` % "shaded",
    `kamon-akka-http` % "shaded",
    `kamon-play` % "shaded",
    `kamon-redis` % "shaded",
    `kamon-okhttp` % "shaded",
    `kamon-caffeine` % "shaded",
)

lazy val `bill-of-materials` = (project in file("bill-of-materials"))
  .enablePlugins(BillOfMaterialsPlugin)
  .settings(
    name := "kamon-bom",
    crossVersion := CrossVersion.disabled,
    bomIncludeProjects := coreProjects ++ instrumentationProjects ++ reportersProjects,
    pomExtra := pomExtra.value :+ bomDependenciesListing.value,
  )
