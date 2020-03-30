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

import Tests._

lazy val kamon = (project in file("."))
  .disablePlugins(AssemblyPlugin)
  .settings(noPublishing: _*)
  .settings(crossScalaVersions := Nil)
  .aggregate(core, instrumentation)

lazy val core = (project in file("core"))
  .disablePlugins(AssemblyPlugin)
  .settings(noPublishing: _*)
  .settings(crossScalaVersions := Nil)
  .aggregate(`kamon-core`, `kamon-status-page`, `kamon-testkit`, `kamon-core-tests`, `kamon-core-bench`)

lazy val `kamon-core` = (project in file("core/kamon-core"))
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(AssemblyPlugin)
  .settings(
    name := "kamon-core",
    moduleName := "kamon-core",
    buildInfoKeys := Seq[BuildInfoKey](version),
    buildInfoPackage := "kamon.status",
    scalacOptions ++= { if(scalaBinaryVersion.value == "2.11") Seq("-Ydelambdafy:method") else Seq.empty },
    assemblyShadeRules in assembly := Seq(
      ShadeRule.rename("org.HdrHistogram.**"    -> "kamon.lib.@0").inAll,
      ShadeRule.rename("org.jctools.**"         -> "kamon.lib.@0").inAll,
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
      "org.hdrhistogram"  %  "HdrHistogram" % "2.1.9" % "shaded",
      "org.jctools"       %  "jctools-core" % "2.1.1" % "shaded",
    ),
  )


lazy val `kamon-status-page` = (project in file("core/kamon-status-page"))
  .enablePlugins(AssemblyPlugin)
  .settings(
    name := "kamon-status-page",
    moduleName := "kamon-status-page",
    assemblyShadeRules in assembly := Seq(
      ShadeRule.rename("com.grack.nanojson.**"  -> "kamon.lib.@0").inAll,
      ShadeRule.rename("fi.iki.elonen.**"       -> "kamon.lib.@0").inAll,
    ),
    libraryDependencies ++= Seq(
      "org.nanohttpd" %  "nanohttpd" % "2.3.1" % "shaded",
      "com.grack"     %  "nanojson"  % "1.1"   % "shaded"
    )
  ).dependsOn(`kamon-core` % "provided")


lazy val `kamon-testkit` = (project in file("core/kamon-testkit"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    moduleName := "kamon-testkit",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.0.8" % "test"
    )
  ).dependsOn(`kamon-core`)


lazy val `kamon-core-tests` = (project in file("core/kamon-core-tests"))
  .disablePlugins(AssemblyPlugin)
  .settings(noPublishing: _*)
  .settings(
    moduleName := "kamon-core-tests",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest"        % "3.0.8" % "test",
      "ch.qos.logback" % "logback-classic"  % "1.2.3" % "test"
    )
  ).dependsOn(`kamon-testkit`)


lazy val `kamon-core-bench` = (project in file("core/kamon-core-bench"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JmhPlugin)
  .settings(moduleName := "kamon-core-bench")
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
    `kamon-mongo`,
    `kamon-annotation`,
    `kamon-annotation-api`,
    `kamon-system-metrics`
  )

lazy val `kamon-instrumentation-common` = (project in file("instrumentation/kamon-instrumentation-common"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    name := "kamon-instrumentation-common",
    moduleName := "kamon-instrumentation-common",
    resolvers += Resolver.mavenLocal,
    libraryDependencies ++= Seq(
      scalatest % "test",
      slf4jApi % "test",
      kanelaAgent % "provided"
    )
  ).dependsOn(`kamon-core`, `kamon-testkit` % "test")

lazy val `kamon-executors` = (project in file("instrumentation/kamon-executors"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    moduleName := "kamon-executors",
    testGrouping in Test := groupByExperimentalExecutorTests((definedTests in Test).value, kanelaAgentJar.value),
    libraryDependencies ++=
      providedScope(kanelaAgent) ++
      testScope(scalatest, logbackClassic, "com.google.guava"  % "guava"  % "24.1-jre")
  ).dependsOn(`kamon-core`, `kamon-instrumentation-common`, `kamon-testkit` % "test")

lazy val `kamon-executors-bench` = (project in file("instrumentation/kamon-executors-bench"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JmhPlugin)
  .settings(noPublishing: _*)
  .settings(
    moduleName := "kamon-executors-bench",
    resolvers += Resolver.mavenLocal,
    libraryDependencies ++= compileScope(kanelaAgent, "com.google.guava"  % "guava"  % "24.1-jre")
  ).dependsOn(`kamon-core`, `kamon-instrumentation-common`)


def groupByExperimentalExecutorTests(tests: Seq[TestDefinition], kanelaJar: File): Seq[Group] = {
  val (stable, experimental) = tests.partition(t => t.name != "kamon.instrumentation.executor.CaptureContextOnSubmitInstrumentationSpec")

  val stableGroup = Group("stableTests", stable, SubProcess(
    ForkOptions().withRunJVMOptions(Vector(
      "-javaagent:" + kanelaJar.toString
    ))
  ))

  val experimentalGroup = Group("experimentalTests", experimental, SubProcess(
    ForkOptions().withRunJVMOptions(Vector(
      "-javaagent:" + kanelaJar.toString,
      "-Dkanela.modules.executor-service.enabled=false",
      "-Dkanela.modules.executor-service-capture-on-submit.enabled=true"
    ))
  ))

  Seq(stableGroup, experimentalGroup)
}

val twitterUtilCore  = "com.twitter"   %% "util-core"         % "6.40.0"
val scalazConcurrent = "org.scalaz"    %% "scalaz-concurrent" % "7.2.28"
val catsEffect       = "org.typelevel" %%  "cats-effect"      % "2.1.2"

lazy val `kamon-twitter-future` = (project in file("instrumentation/kamon-twitter-future"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    bintrayPackage := "kamon-futures",
    crossScalaVersions := Seq("2.11.12", "2.12.10", "2.13.1"),
    libraryDependencies ++=
      providedScope(kanelaAgent, twitterUtilCore) ++
      testScope(scalatest, logbackClassic)
  ).dependsOn(`kamon-core`, `kamon-executors`, `kamon-testkit` % "test")

lazy val `kamon-scalaz-future` = (project in file("instrumentation/kamon-scalaz-future"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    bintrayPackage := "kamon-futures",
    libraryDependencies ++=
      providedScope(kanelaAgent, scalazConcurrent) ++
      testScope(scalatest,  logbackClassic)
  ).dependsOn(`kamon-core`, `kamon-executors`, `kamon-testkit` % "test")

lazy val `kamon-scala-future` = (project in file("instrumentation/kamon-scala-future"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    bintrayPackage := "kamon-futures",
    libraryDependencies ++=
      providedScope(kanelaAgent) ++
      testScope(scalatest, logbackClassic)
  ).dependsOn(`kamon-core`, `kamon-executors`, `kamon-testkit` % "test")

lazy val `kamon-cats-io` = (project in file("instrumentation/kamon-cats-io"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    bintrayPackage := "kamon-futures",
    crossScalaVersions := Seq("2.11.12", "2.12.10", "2.13.1"),
    libraryDependencies ++=
      providedScope(catsEffect, kanelaAgent) ++
      testScope(scalatest, logbackClassic)
  ).dependsOn(`kamon-core`, `kamon-executors`, `kamon-testkit` % "test")


lazy val `kamon-logback` = (project in file("instrumentation/kamon-logback"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    moduleName := "kamon-logback",
    libraryDependencies ++=
      providedScope(kanelaAgent, "ch.qos.logback"  %   "logback-classic" % "1.2.3") ++
      testScope(scalatest)
  ).dependsOn(`kamon-core`, `kamon-instrumentation-common`, `kamon-testkit` % "test")

val slick               = "com.typesafe.slick"       %% "slick"                     % "3.3.2"
val slickHikari         = "com.typesafe.slick"       %% "slick-hikaricp"            % "3.3.2"
val h2                  = "com.h2database"            % "h2"                        % "1.4.182"
val sqlite              = "org.xerial"                % "sqlite-jdbc"               % "3.27.2.1"
val mariaConnector      = "org.mariadb.jdbc"          % "mariadb-java-client"       % "2.2.6"
val mariaDB4j           = "ch.vorburger.mariaDB4j"    % "mariaDB4j"                 % "2.4.0"
val postgres            = "org.postgresql"            % "postgresql"                % "42.2.5"
val hikariCP            = "com.zaxxer"                % "HikariCP"                  % "2.6.2"

lazy val `kamon-jdbc` = (project in file("instrumentation/kamon-jdbc"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    moduleName := "kamon-jdbc",
    libraryDependencies ++=
      providedScope(kanelaAgent, hikariCP, mariaConnector, slick, postgres) ++
      testScope(h2, sqlite, mariaDB4j, postgres,  slickHikari, scalatest, slf4jApi, logbackClassic)
  ).dependsOn(`kamon-core`, `kamon-executors`, `kamon-testkit` % "test")

val mongoSyncDriver   = "org.mongodb"         %   "mongodb-driver-sync"             % "3.11.0"
val mongoScalaDriver  = "org.mongodb.scala"   %%  "mongo-scala-driver"              % "2.7.0"
val mongoDriver       = "org.mongodb"         %   "mongodb-driver-reactivestreams"  % "1.12.0"
val embeddedMongo     = "de.flapdoodle.embed" %   "de.flapdoodle.embed.mongo"       % "2.2.0"

lazy val `kamon-mongo` = (project in file("instrumentation/kamon-mongo"))
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    moduleName := "kamon-mongo",
    libraryDependencies ++=
      providedScope(kanelaAgent, mongoDriver, mongoSyncDriver, mongoScalaDriver) ++
      testScope(embeddedMongo, scalatest)
  ).dependsOn(`kamon-core`, `kamon-instrumentation-common`, `kamon-testkit` % "test")


lazy val `kamon-annotation-api` = (project in file("instrumentation/kamon-annotation-api"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    moduleName := "kamon-annotation-api",
    resolvers += Resolver.mavenLocal,
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
    moduleName := "kamon-annotation",
    assemblyShadeRules in assembly := Seq(
      ShadeRule.rename("javax.el.**"    -> "kamon.lib.@0").inAll,
      ShadeRule.rename("com.sun.el.**"    -> "kamon.lib.@0").inAll,
    ),
    libraryDependencies ++=
      Seq("org.glassfish" % "javax.el" % "3.0.1-b11" % "shaded") ++
      providedScope(kanelaAgent) ++
      testScope(scalatest, logbackClassic)
  ).dependsOn(`kamon-core`, `kamon-annotation-api`, `kamon-testkit` % "test")

lazy val `kamon-system-metrics` = (project in file("instrumentation/kamon-system-metrics"))
  .disablePlugins(AssemblyPlugin)
  .settings(instrumentationSettings: _*)
  .settings(
    moduleName := "kamon-system-metrics",
    libraryDependencies ++=
      Seq("com.github.oshi" % "oshi-core" % "4.2.1") ++
      providedScope(kanelaAgent) ++
      testScope(scalatest, logbackClassic)
  ).dependsOn(`kamon-core`)

lazy val `kamon-akka` = (project in file("instrumentation/kamon-akka"))
  .enablePlugins(JavaAgent)
  .disablePlugins(AssemblyPlugin)
  .settings(instrumentationSettings: _*)
  .settings(
    moduleName := "kamon-akka",
  ).dependsOn(`kamon-scala-future` % "compile,common,akka-2.5,akka-2.6", `kamon-testkit` % "test,test-common,test-akka-2.5,test-akka-2.6")
