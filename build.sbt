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
  .aggregate(core, instrumentation)

lazy val core = (project in file("core"))
  .disablePlugins(AssemblyPlugin)
  .settings(noPublishing: _*)
  .aggregate(`kamon-core`, `kamon-status-page`, `kamon-testkit`, `kamon-core-tests`, `kamon-core-bench`)

lazy val `kamon-core` = (project in file("core/kamon-core"))
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(AssemblyPlugin)
  .settings(
    name := "kamon-core",
    moduleName := "kamon-core",
    buildInfoKeys := Seq[BuildInfoKey](version),
    buildInfoPackage := "kamon.status",
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
  .aggregate(`kamon-instrumentation-common`)

lazy val `kamon-instrumentation-common` = (project in file("instrumentation/kamon-instrumentation-common"))
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

import Tests._

val commonSettings = Seq(
  crossScalaVersions := List("2.11.12", "2.12.8", "2.13.0"),
  resolvers += Resolver.mavenLocal,
  resolvers += Resolver.bintrayRepo("kamon-io", "snapshots")
)

lazy val `kamon-executors` = (project in file("kamon-executors"))
  .settings(commonSettings: _*)
  .settings(
    moduleName := "kamon-executors",
    testGrouping in Test := groupByExperimentalExecutorTests((definedTests in Test).value, kanelaAgentJar.value),
    libraryDependencies ++=
      providedScope(kanelaAgent) ++
      testScope(scalatest, logbackClassic, "com.google.guava"  % "guava"  % "24.1-jre")
  ).dependsOn(`kamon-core`, `kamon-instrumentation-common`, `kamon-testkit` % "test")

lazy val benchmark = (project in file("kamon-executors-bench"))
  .enablePlugins(JmhPlugin)
  .settings(noPublishing: _*)
  .settings(commonSettings: _*)
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