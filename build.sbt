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
      "com.typesafe"      %  "config"       % "1.4.3",
      "org.slf4j"         %  "slf4j-api"    % "1.7.36",
      "org.hdrhistogram"  %  "HdrHistogram" % "2.1.9" % "provided,shaded",
      "org.jctools"       %  "jctools-core" % "3.3.0" % "provided,shaded",
      "com.oracle.substratevm" % "svm"      % "19.2.1" % "provided"
    ),
  )


lazy val `kamon-status-page` = (project in file("core/kamon-status-page"))
  .enablePlugins(AssemblyPlugin)
  .settings(
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
    libraryDependencies += scalatest % "provided,test"
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
val instrumentationProjects = Seq[ProjectReference](
  `kamon-instrumentation-common`,
  `kamon-executors`,
  `kamon-executors-bench`,
  `kamon-scala-future`,
  `kamon-twitter-future`,
  `kamon-scalaz-future`,
  `kamon-cats-io`,
  `kamon-cats-io-3`,
  `kamon-zio-2`,
  `kamon-logback`,
  `kamon-jdbc`,
  `kamon-kafka`,
  `kamon-mongo`,
  `kamon-mongo-legacy`,
  `kamon-cassandra`,
  `kamon-elasticsearch`,
  `kamon-opensearch`,
  `kamon-spring`,
  `kamon-annotation`,
  `kamon-annotation-api`,
  `kamon-system-metrics`,
  `kamon-akka`,
  `kamon-akka-http`,
  `kamon-akka-grpc`,
  `kamon-pekko`,
  `kamon-pekko-http`,
  `kamon-pekko-grpc`,
  `kamon-play`,
  `kamon-okhttp`,
  `kamon-tapir`,
  `kamon-redis`,
  `kamon-caffeine`,
  `kamon-lagom`,
  `kamon-finagle`,
  `kamon-aws-sdk`,
  `kamon-alpakka-kafka`,
  `kamon-http4s-1_0`,
  `kamon-http4s-0_23`,
  `kamon-apache-httpclient`,
  `kamon-apache-cxf`
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
      "com.google.guava" % "guava" % "33.0.0-jre",
      kanelaAgent % "provided",
    )
  ).dependsOn(`kamon-core`, `kamon-instrumentation-common`)


lazy val `kamon-twitter-future` = (project in file("instrumentation/kamon-twitter-future"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    crossScalaVersions := Seq(`scala_2.11_version`, `scala_2.12_version`, `scala_2.13_version`),
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
    crossScalaVersions := Seq(`scala_2.11_version`, `scala_2.12_version`, `scala_2.13_version`),
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
    crossScalaVersions := Seq(`scala_2.11_version`, `scala_2.12_version`, `scala_2.13_version`),
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

lazy val `kamon-cats-io-3` = (project in file("instrumentation/kamon-cats-io-3"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    crossScalaVersions := Seq(`scala_2.12_version`, `scala_2.13_version`, scala_3_version),
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      "org.typelevel" %% "cats-effect" % "3.3.14" % "provided",
      scalatest % "test",
      logbackClassic % "test"
    ),

  ).dependsOn(`kamon-core`, `kamon-executors`, `kamon-testkit` % "test")

lazy val `kamon-zio-2` = (project in file("instrumentation/kamon-zio-2"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    crossScalaVersions := Seq(`scala_2.12_version`, `scala_2.13_version`, scala_3_version),
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      "dev.zio" %% "zio" % "2.0.21" % "provided",
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


def slickVersion(scalaVersion: String) = scalaVersion match {
  case "3" => "3.5.0-M5"
  case x   => "3.3.2"
}
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
      "com.typesafe.slick"       %% "slick"                     % slickVersion(scalaBinaryVersion.value) % "provided",
      "org.postgresql"            % "postgresql"                % "42.2.5" % "provided",

      scalatest % "test",
      logbackClassic % "test",
      "com.typesafe.slick"       %% "slick-hikaricp"            % slickVersion(scalaBinaryVersion.value) % "test",
      "com.h2database"            % "h2"                        % "1.4.192" % "test",
      "org.xerial"                % "sqlite-jdbc"               % "3.34.0" % "test",
      "ch.vorburger.mariaDB4j"    % "mariaDB4j"                 % "2.5.3" % "test"
    )
  ).dependsOn(`kamon-core`, `kamon-executors`, `kamon-testkit` % "test")


lazy val `kamon-kafka` = (project in file("instrumentation/kamon-kafka"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    libraryDependencies ++= Seq(
      kanelaAgent                 % "provided",
      "org.apache.kafka"          % "kafka-clients"     % "3.4.0" % "provided",

      scalatest                   % "test",
      logbackClassic              % "test",
      "org.testcontainers"        % "kafka"             % "1.17.6" % "test"
    )
  ).dependsOn(`kamon-core`, `kamon-executors`, `kamon-testkit` % "test")


lazy val `kamon-mongo-legacy` = (project in file("instrumentation/kamon-mongo-legacy"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    crossScalaVersions := Seq(`scala_2.11_version`, `scala_2.12_version`, `scala_2.13_version`),
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
    crossScalaVersions := Seq(`scala_2.11_version`, `scala_2.12_version`, `scala_2.13_version`),
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
  .settings(crossScalaVersions := Seq(`scala_2.11_version`, `scala_2.12_version`, `scala_2.13_version`))
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
      "com.dimafeng" %% "testcontainers-scala" % "0.41.0" % "test",
      "com.dimafeng" %% "testcontainers-scala-elasticsearch" % "0.41.0" % "test"
    )
  ).dependsOn(`kamon-core`, `kamon-instrumentation-common`, `kamon-testkit` % "test")

lazy val `kamon-opensearch` = (project in file("instrumentation/kamon-opensearch"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    Test / run / fork := true,
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      "org.opensearch.client" % "opensearch-rest-client" % "1.3.14" % "provided",
      "org.opensearch.client" % "opensearch-rest-high-level-client" % "1.3.14" % "provided",
      scalatest % "test",
      logbackClassic % "test",
      "com.dimafeng" %% "testcontainers-scala" % "0.41.0" % "test",
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
  case "3"    => "10.5.0"
  case _      => "10.2.8"
}
def akkaStreamVersion(scalaVersion: String) = scalaVersion match {
  case "3" => "2.7.0"
  case _   => "2.5.32"
}
def akkaGrpcRuntimeVersion(scalaVersion: String) = scalaVersion match {
  case "3" => "2.3.0"
  case _   => "2.1.3"
}

def versionedScalaSourceDirectories(sourceDir: File, scalaVersion: String): List[File] =
  scalaVersion match {
    case "3"    => List(sourceDir / "scala-2.13+")
    case "2.13" => List(sourceDir / "scala-2.13+")
    case _      => Nil
  }

lazy val `kamon-akka-http` = (project in file("instrumentation/kamon-akka-http"))
  .enablePlugins(JavaAgent)
  .disablePlugins(AssemblyPlugin)
  .settings(instrumentationSettings)
  .settings(Seq(
    Compile / unmanagedSourceDirectories ++= versionedScalaSourceDirectories((Compile / sourceDirectory).value, scalaBinaryVersion.value),
    resolvers += Resolver.bintrayRepo("hseeberger", "maven"),
    javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.10" % "test",
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion(scalaBinaryVersion.value) % "provided",
      "com.typesafe.akka" %% "akka-http2-support"   % akkaHttpVersion(scalaBinaryVersion.value)  % "provided",
      "com.typesafe.akka" %% "akka-stream"          % akkaStreamVersion(scalaBinaryVersion.value) % "provided",

      scalatest % "test",
      slf4jApi % "test",
      slf4jnop % "test",
      okHttp % "test",
      "com.typesafe.akka" %% "akka-http-testkit"    % akkaHttpVersion(scalaBinaryVersion.value) % "test",
      "de.heikoseeberger" %% "akka-http-json4s"     % "1.27.0" % "test" cross CrossVersion.for3Use2_13 intransitive(),
      "org.json4s"        %% "json4s-native"        % "4.0.6" % "test",
    )))
  .dependsOn(`kamon-akka`, `kamon-testkit` % "test")



lazy val `kamon-pekko` = (project in file("instrumentation/kamon-pekko"))
  .enablePlugins(JavaAgent)
  .disablePlugins(AssemblyPlugin)
  .settings(instrumentationSettings: _*)
  .settings(Seq(
    crossScalaVersions := Seq(`scala_2.12_version`, `scala_2.13_version`, scala_3_version),
    libraryDependencies ++= Seq(
      "org.apache.pekko" %% "pekko-actor" % pekkoHttpVersion % "provided"
    )
  ))
  .dependsOn(
    `kamon-scala-future` % "compile",
    `kamon-testkit` % "test"
  )

lazy val pekkoHttpVersion = "1.0.0"

lazy val `kamon-pekko-http` = (project in file("instrumentation/kamon-pekko-http"))
  .enablePlugins(JavaAgent)
  .disablePlugins(AssemblyPlugin)
  .settings(instrumentationSettings)
  .settings(Seq(
    crossScalaVersions := Seq(`scala_2.12_version`, `scala_2.13_version`, scala_3_version),
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      "org.apache.pekko" %% "pekko-http"          % pekkoHttpVersion % "provided",
      "org.apache.pekko" %% "pekko-stream"        % "1.0.1" % "provided",
      scalatest % "test",
      slf4jApi % "test",
      slf4jnop % "test",
      okHttp % "test",
      "org.apache.pekko" %% "pekko-http-testkit"    % pekkoHttpVersion % "test",
      "com.github.pjfanning" %% "pekko-http-json4s" % "2.0.0" % "test",
      "org.json4s"        %% "json4s-native"        % "4.0.6" % "test",
    ),
  )).dependsOn(`kamon-pekko`, `kamon-testkit` % "test")

lazy val `kamon-pekko-grpc` = (project in file("instrumentation/kamon-pekko-grpc"))
  .enablePlugins(JavaAgent, PekkoGrpcPlugin)
  .disablePlugins(AssemblyPlugin)
  .settings(instrumentationSettings)
  .settings(Seq(
    PB.additionalDependencies := Seq.empty,
    crossScalaVersions := Seq(`scala_2.12_version`, `scala_2.13_version`, scala_3_version),
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",

      "org.apache.pekko" %% "pekko-http" % pekkoHttpVersion % "provided",
      "org.apache.pekko" %% "pekko-stream" % "1.0.1" % "provided",
      "org.apache.pekko" %% "pekko-discovery"% "1.0.0" % "provided",

      "com.thesamet.scalapb"    %% "scalapb-runtime"   % "0.11.8" % "provided",
      "org.apache.pekko"        %% "pekko-grpc-runtime" % "1.0.0" % "provided",
      "io.grpc"                 %  "grpc-stub"         % "1.43.2" % "provided",


      scalatest % "test",
      slf4jApi % "test",
      logbackClassic % "test",
    )
  )).dependsOn(`kamon-pekko-http`, `kamon-testkit` % "test")

lazy val `kamon-akka-grpc` = (project in file("instrumentation/kamon-akka-grpc"))
  .enablePlugins(JavaAgent, AkkaGrpcPlugin)
  .disablePlugins(AssemblyPlugin)
  .settings(instrumentationSettings)
  .settings(Seq(
    PB.additionalDependencies := Seq.empty,
    crossScalaVersions := Seq(`scala_2.12_version`, `scala_2.13_version`, scala_3_version),
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",

      "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion(scalaBinaryVersion.value) % "provided",
      "com.typesafe.akka" %% "akka-http2-support"   % akkaHttpVersion(scalaBinaryVersion.value)  % "provided",
      "com.typesafe.akka" %% "akka-stream"          % akkaStreamVersion(scalaBinaryVersion.value) % "provided",
      "com.typesafe.akka" %% "akka-discovery"       % akkaStreamVersion(scalaBinaryVersion.value) % "provided",

      // gRPC-specific dependencies provided by the sbt-akka-grpc plugin. We
      "com.thesamet.scalapb"    %% "scalapb-runtime"   % "0.11.8" % "provided",
      "com.lightbend.akka.grpc" %% "akka-grpc-runtime" % akkaGrpcRuntimeVersion(scalaBinaryVersion.value)  % "provided",
      "io.grpc"                 %  "grpc-stub"         % "1.43.2" % "provided",

      scalatest % "test",
      slf4jApi % "test",
      logbackClassic % "test",
    ),
  )).dependsOn(`kamon-akka-http`, `kamon-testkit` % "test")


lazy val `kamon-play` = (project in file("instrumentation/kamon-play"))
  .enablePlugins(JavaAgent)
  .disablePlugins(AssemblyPlugin)
  .settings(instrumentationSettings,
    crossScalaVersions := Seq(`scala_2.11_version`, `scala_2.12_version`, `scala_2.13_version`))
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
      "com.squareup.okhttp3"      % "okhttp"                    % "4.12.0" % "provided",

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
    crossScalaVersions := Seq(`scala_2.12_version`, `scala_2.13_version`),
    libraryDependencies ++= Seq(
      kanelaAgent % "provided,legacy",

      // Tapir 1.x dependencies
      "com.softwaremill.sttp.tapir" %% "tapir-core" % "1.0.1" % "provided",
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion(scalaBinaryVersion.value) % "provided",
      "com.softwaremill.sttp.tapir" %% "tapir-akka-http-server" % "1.0.5" % "test",

      // Legacy Tapir dependencies
      "com.softwaremill.sttp.tapir" %% "tapir-core" % "0.20.2" % "legacy",
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion(scalaBinaryVersion.value) % "legacy",
      "com.softwaremill.sttp.tapir" %% "tapir-akka-http-server" % "0.20.2" % "test,test-legacy",
      "com.softwaremill.sttp.client3" %% "core" % "3.3.0-RC2" % "test,test-legacy",
      scalatest % "test,test-legacy",
      logbackClassic % "test,test-legacy",
    )
  )
  .dependsOn(`kamon-core` % "compile,legacy", `kamon-akka-http` % "compile,legacy", `kamon-testkit` % "test,test-legacy")

lazy val `kamon-redis` = (project in file("instrumentation/kamon-redis"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      "redis.clients" % "jedis"  % "3.6.0" % "provided",
      "io.lettuce" % "lettuce-core"  % "6.1.2.RELEASE" % "provided",

      scalatest % "test",
      logbackClassic % "test",
      "org.testcontainers" % "testcontainers" % "1.15.3" % "test",
    ) :+ (if (scalaVersion.value.startsWith("2.11")) "com.github.etaty" %% "rediscala" % "1.9.0" % "provided"
          else "io.github.rediscala" %% "rediscala" % "1.13.0" % "provided")
  ).dependsOn(`kamon-core`, `kamon-instrumentation-common`, `kamon-testkit` % "test")

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
  ).dependsOn(`kamon-core`, `kamon-instrumentation-common`, `kamon-testkit` % "test")


lazy val `kamon-lagom` = (project in file("instrumentation/kamon-lagom"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    crossScalaVersions := Seq(`scala_2.11_version`, `scala_2.12_version`, `scala_2.13_version`),
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, scalaMajor)) if scalaMajor == 11 => providedScope("com.lightbend.lagom" %% "lagom-server" % "1.4.13")
        case _ => providedScope("com.lightbend.lagom" %% "lagom-server" % "1.6.7")
      }
    }
  )
  .dependsOn(`kamon-core` % "compile")

lazy val `kamon-finagle` = (project in file("instrumentation/kamon-finagle"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    crossScalaVersions := Seq(`scala_2.12_version`, `scala_2.13_version`),
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      "com.twitter" %% "finagle-http" % "21.12.0" % "provided",
      "com.twitter" %% "bijection-util" % "0.9.7" % "test",
      scalatest % "test",
      logbackClassic % "test",
    )
  ).dependsOn(`kamon-core`, `kamon-instrumentation-common`, `kamon-testkit` % "test")

lazy val `kamon-aws-sdk` = (project in file("instrumentation/kamon-aws-sdk"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      "com.amazonaws" % "aws-java-sdk-lambda" % "1.12.225" % "provided",
      "software.amazon.awssdk" % "dynamodb" % "2.17.191" % "provided",
      "software.amazon.awssdk" % "sqs" % "2.17.191" % "provided",

      scalatest % "test",
      logbackClassic % "test",
      "org.testcontainers" % "dynalite" % "1.17.1" % "test"
    )
  ).dependsOn(`kamon-core`, `kamon-executors`, `kamon-testkit` % "test")

lazy val `kamon-alpakka-kafka` = (project in file("instrumentation/kamon-alpakka-kafka"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    crossScalaVersions := Seq(`scala_2.12_version`, `scala_2.13_version`),
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      "com.typesafe.akka" %% "akka-stream-kafka" % "2.1.1" % "provided",
      "com.typesafe.akka" %% "akka-stream" % "2.6.19" % "provided",

      scalatest % "test",
      logbackClassic % "test"
    )
  ).dependsOn(`kamon-core`, `kamon-akka`, `kamon-testkit` % "test")

lazy val `kamon-http4s-1_0` = (project in file("instrumentation/kamon-http4s-1.0"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    name := "kamon-http4s-1.0",
    crossScalaVersions := Seq(`scala_2.13_version`, `scala_3_version`),
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-client" % "1.0.0-M38" % Provided,
      "org.http4s" %% "http4s-server" % "1.0.0-M38" % Provided,
      "org.http4s" %% "http4s-blaze-client" % "1.0.0-M38" % Test,
      "org.http4s" %% "http4s-blaze-server" % "1.0.0-M38" % Test,
      "org.http4s" %% "http4s-dsl" % "1.0.0-M38" % Test,
      scalatest % Test,
    )
  )
  .dependsOn(
      `kamon-core`,
      `kamon-instrumentation-common`,
      `kamon-testkit` % Test)

lazy val `kamon-http4s-0_23` = (project in file("instrumentation/kamon-http4s-0.23"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    name := "kamon-http4s-0.23",
    scalacOptions ++= { if(scalaBinaryVersion.value == "2.12") Seq("-Ypartial-unification") else Seq.empty },
    crossScalaVersions := Seq(`scala_2.12_version`, `scala_2.13_version`, `scala_3_version`),
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-client" % "0.23.19" % Provided,
      "org.http4s" %% "http4s-server" % "0.23.19" % Provided,
      "org.http4s" %% "http4s-blaze-client" % "0.23.14" % Test,
      "org.http4s" %% "http4s-blaze-server" % "0.23.14" % Test,
      "org.http4s" %% "http4s-dsl" % "0.23.19" % Test,
      scalatest % Test
    )
  )
  .dependsOn(
    `kamon-core`,
    `kamon-instrumentation-common`,
    `kamon-testkit` % Test
  )

lazy val `kamon-apache-httpclient` = (project in file("instrumentation/kamon-apache-httpclient"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      "org.apache.httpcomponents" % "httpclient" % "4.0" % "provided",
      slf4jApi % "provided",

      scalatest % "test",
      logbackClassic % "test",
      "org.mock-server" % "mockserver-client-java" % "5.13.2" % "test",
      "com.dimafeng" %% "testcontainers-scala" % "0.41.0" % "test",
      "com.dimafeng" %% "testcontainers-scala-mockserver" % "0.41.0" % "test"
    )
  ).dependsOn(`kamon-core`, `kamon-executors`, `kamon-testkit` % "test")

lazy val `kamon-apache-cxf` = (project in file("instrumentation/kamon-apache-cxf"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    crossScalaVersions := Seq(`scala_2.13_version`, `scala_3_version`),
    libraryDependencies ++= Seq(
      kanelaAgent % "provided",
      "org.apache.cxf" % "cxf-rt-frontend-simple" % "3.3.6" % "provided",
      slf4jApi % "provided",

      scalatest % "test",
      logbackClassic % "test",
      "org.mock-server" % "mockserver-client-java" % "5.13.2" % "test",
      "com.dimafeng" %% "testcontainers-scala" % "0.41.0" % "test",
      "com.dimafeng" %% "testcontainers-scala-mockserver" % "0.41.0" % "test",
      "org.apache.cxf" % "cxf-rt-frontend-jaxws" % "3.3.6" % "test",
      "org.apache.cxf" % "cxf-rt-transports-http" % "3.3.6" % "test",
    )
  ).dependsOn(`kamon-core`, `kamon-executors`, `kamon-testkit` % "test")

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
      "com.grack" % "nanojson" % "1.8",

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
      "com.google.protobuf"   % "protobuf-java"   % "3.21.7"  % "provided,shaded",

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
    libraryDependencies += scalatest % Test,
  ).dependsOn(`kamon-core`)


lazy val `kamon-zipkin` = (project in file("reporters/kamon-zipkin"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "io.zipkin.reporter2" % "zipkin-reporter" % "3.3.0",
      "io.zipkin.reporter2" % "zipkin-sender-okhttp3" % "3.3.0",
      scalatest % "test"
    )
  ).dependsOn(`kamon-core`)


lazy val `kamon-jaeger` = (project in file("reporters/kamon-jaeger"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "io.jaegertracing" % "jaeger-thrift" % "1.8.1",
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
      "com.newrelic.telemetry" % "telemetry-core" % "0.16.0",
      "com.newrelic.telemetry" % "telemetry-http-okhttp" % "0.16.0",
      scalatest % "test",
      "org.mockito" % "mockito-core" % "3.12.4" % "test"
    )
  ).dependsOn(`kamon-core`)

lazy val `kamon-opentelemetry` = (project in file("reporters/kamon-opentelemetry"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "io.opentelemetry" % "opentelemetry-exporter-otlp" % "1.35.0",
      // Compile-time dependency required in scala 3
      "com.google.auto.value" % "auto-value-annotations" % "1.9" % "compile",

      scalatest % "test",
      logbackClassic % "test"
    )
  ).dependsOn(`kamon-core`, `kamon-testkit` % "test")

lazy val `kamon-prometheus` = (project in file("reporters/kamon-prometheus"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    libraryDependencies ++= Seq(
      okHttp,
      scalatest % "test",
      logbackClassic % "test"
    )
  ).dependsOn(`kamon-core`, `kamon-testkit` % "test")


/**
  *  Bundle Projects
  */
lazy val bundle = (project in file("bundle"))
  .disablePlugins(AssemblyPlugin)
  .settings(noPublishing: _*)
  .settings(crossScalaVersions := Nil)
  .aggregate(
    `kamon-bundle`,
    `kamon-bundle_2_11`,
    `kamon-bundle-3`,
    `kamon-runtime-attacher`
  )

import com.lightbend.sbt.javaagent.Modules
import BundleKeys._

lazy val commonBundleSettings = Seq(
  moduleName := "kamon-bundle",
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
    oshiCore
  )
)

lazy val `kamon-runtime-attacher` = (project in file("bundle/kamon-runtime-attacher"))
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(AssemblyPlugin)
  .settings(
    moduleName := "kamon-runtime-attacher",
    buildInfoPackage := "kamon.runtime.attacher",
    buildInfoKeys := Seq[BuildInfoKey](kanelaAgentJarName),
    kanelaAgentJar := update.value.matching(Modules.exactFilter(kanelaAgent)).head,
    kanelaAgentJarName := kanelaAgentJar.value.getName,
    Compile / resourceGenerators += Def.task(Seq(kanelaAgentJar.value)).taskValue,
    assembly / assemblyShadeRules := Seq(
      ShadeRule.zap("**module-info").inAll,
      ShadeRule.rename("net.bytebuddy.agent.**" -> "kamon.lib.@0").inAll
    ),
    libraryDependencies ++= Seq(
      slf4jApi % "compile",
      kanelaAgent % "provided",
      "net.bytebuddy" % "byte-buddy-agent" % "1.12.7" % "provided,shaded"
    )
  )


/**
  *   Add a reference here to all the project dependencies that can be built
  *   for Scala 2.11, 2.12, and 2.13.
  */
lazy val `kamon-bundle-dependencies-all` = (project in file("bundle/kamon-bundle-dependencies-all"))
  .disablePlugins(AssemblyPlugin)
  .settings(noPublishing: _*)
  .settings(ideSkipProject: _*)
  .dependsOn(
    `kamon-runtime-attacher`,
    `kamon-status-page`,
    `kamon-instrumentation-common`,
    `kamon-executors`,
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
    `kamon-opensearch`,
    `kamon-spring`,
    `kamon-annotation`,
    `kamon-annotation-api`,
    `kamon-system-metrics`,
    `kamon-akka`,
    `kamon-akka-http`,
    `kamon-play`,
    `kamon-redis`,
    `kamon-okhttp`,
    `kamon-caffeine`,
    `kamon-lagom`,
    `kamon-aws-sdk`,
    `kamon-apache-httpclient`,
    `kamon-apache-cxf`
  )

/**
  *   Add a reference here to all the project dependencies that can be built
  *   from 2.12. Currently only Scala 2.12 and 2.13.
  */
lazy val `kamon-bundle-dependencies-2-12-and-up` = (project in file("bundle/kamon-bundle-dependencies-2-12-and-up"))
  .disablePlugins(AssemblyPlugin)
  .settings(noPublishing: _*)
  .settings(ideSkipProject: _*)
  .settings(
    crossScalaVersions := Seq(
      `scala_2.12_version`,
      `scala_2.13_version`
    )
  )
  .dependsOn(
    `kamon-bundle-dependencies-all`,
    `kamon-akka-grpc`,
    `kamon-cats-io-3`,
    `kamon-zio-2`,
    `kamon-finagle`,
    `kamon-pekko`,
    `kamon-pekko-http`,
    `kamon-pekko-grpc`,
    `kamon-tapir`,
    `kamon-alpakka-kafka`
  )

/**
  *   Add a reference here to all the project dependencies that can be built
  *   for 3
  */
lazy val `kamon-bundle-dependencies-3` = (project in file("bundle/kamon-bundle-dependencies-3"))
  .disablePlugins(AssemblyPlugin)
  .settings(noPublishing: _*)
  .settings(ideSkipProject: _*)
  .dependsOn(
    `kamon-runtime-attacher`,
    `kamon-status-page`,
    `kamon-instrumentation-common`,
    `kamon-executors`,
    `kamon-scala-future`,
    `kamon-logback`,
    `kamon-jdbc`,
    `kamon-kafka`,
    `kamon-elasticsearch`,
    `kamon-opensearch`,
    `kamon-spring`,
    `kamon-annotation`,
    `kamon-annotation-api`,
    `kamon-system-metrics`,
    `kamon-akka`,
    `kamon-akka-http`,
    `kamon-akka-grpc`,
    `kamon-redis`,
    `kamon-okhttp`,
    `kamon-caffeine`,
    `kamon-aws-sdk`,
    `kamon-cats-io-3`,
    `kamon-zio-2`,
    `kamon-pekko`,
    `kamon-pekko-http`,
    `kamon-pekko-grpc`,
    `kamon-apache-httpclient`,
    `kamon-apache-cxf`
  )

lazy val `kamon-bundle` = (project in file("bundle/kamon-bundle"))
  .enablePlugins(AssemblyPlugin)
  .settings(commonBundleSettings)
  .settings(ideSkipProject: _*)
  .settings(
    crossScalaVersions := Seq(
      `scala_2.12_version`,
      `scala_2.13_version`
    )
  )
  .dependsOn(
    `kamon-core`,
    `kamon-bundle-dependencies-2-12-and-up` % "shaded"
  )

lazy val `kamon-bundle-3` = (project in file("bundle/kamon-bundle-3"))
  .enablePlugins(AssemblyPlugin)
  .settings(commonBundleSettings)
  .settings(ideSkipProject: _*)
  .settings(
    scalaVersion := scala_3_version,
    crossScalaVersions := Seq(scala_3_version)
  )
  .dependsOn(
    `kamon-core`,
    `kamon-bundle-dependencies-3` % "shaded"
  )

lazy val `kamon-bundle_2_11` = (project in file("bundle/kamon-bundle_2.11"))
  .enablePlugins(AssemblyPlugin)
  .settings(commonBundleSettings)
  .settings(ideSkipProject: _*)
  .settings(
    scalaVersion := `scala_2.11_version`,
    crossScalaVersions := Seq(
      `scala_2.11_version`
    ),
  )
  .dependsOn(
    `kamon-core`,
    `kamon-bundle-dependencies-all` % "shaded"
  )


lazy val `bill-of-materials` = (project in file("bill-of-materials"))
  .enablePlugins(BillOfMaterialsPlugin)
  .settings(ideSkipProject: _*)
  .settings(
    name := "kamon-bom",
    crossVersion := CrossVersion.disabled,
    bomIncludeProjects := coreProjects ++ instrumentationProjects ++ reportersProjects,
    pomExtra := pomExtra.value :+ bomDependenciesListing.value,
  )
