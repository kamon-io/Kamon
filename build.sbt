/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

val play26Version     = "2.6.23"
val play27Version     = "2.7.3"

val kamonCore         = "io.kamon"  %%  "kamon-core"                    % "2.0.0"
val kamonTestkit      = "io.kamon"  %%  "kamon-testkit"                 % "2.0.0"
val kamonScala        = "io.kamon"  %%  "kamon-scala-future"            % "2.0.0"
val kamonCommon       = "io.kamon"  %%  "kamon-instrumentation-common"  % "2.0.0"
val kamonAkkaHttp     = "io.kamon"  %%  "kamon-akka-http"               % "2.0.0"
val kanelaAgent       = "io.kamon"  %   "kanela-agent"                  % "1.0.0"

val play              = "com.typesafe.play"       %%  "play"                  % play27Version
val playNetty         = "com.typesafe.play"       %%  "play-netty-server"     % play27Version
val playAkkaHttp      = "com.typesafe.play"       %%  "play-akka-http-server" % play27Version
val playWS            = "com.typesafe.play"       %%  "play-ws"               % play27Version
val playLogback       = "com.typesafe.play"       %%  "play-logback"          % play27Version
val playTest          = "com.typesafe.play"       %%  "play-test"             % play27Version
val scalatestPlus     = "org.scalatestplus.play"  %%  "scalatestplus-play"    % "4.0.3"


lazy val root = Project("kamon-play", file("."))
  .settings(noPublishing: _*)
  .settings(crossScalaVersions := Nil)
  .aggregate(instrumentation, commonTests, testsOnPlay26, testsOnPlay27)


lazy val instrumentation = Project("instrumentation", file("kamon-play"))
  .enablePlugins(JavaAgent)
  .settings(
    name := "instrumentation",
    bintrayPackage := "kamon-play",
    moduleName := "kamon-play",
    scalaVersion := "2.12.8",
    crossScalaVersions := Seq("2.11.12", "2.12.8", "2.13.0"),
    testGrouping in Test := singleTestPerJvm((definedTests in Test).value, (javaOptions in Test).value),
    libraryDependencies ++=
      compileScope(kamonCore, kamonScala, kamonAkkaHttp, kamonCommon) ++
      providedScope(play, playNetty, playAkkaHttp, playWS, kanelaAgent))


lazy val commonTests = Project("common-tests", file("common-tests"))
  .dependsOn(instrumentation)
  .settings(noPublishing: _*)
  .settings(
    test := ((): Unit),
    testOnly := ((): Unit),
    testQuick := ((): Unit),
    scalaVersion := "2.12.8",
    libraryDependencies ++=
      compileScope(kamonCore, kamonScala, kamonAkkaHttp, kamonCommon) ++
      providedScope(play, playNetty, playAkkaHttp, playWS, kanelaAgent) ++
      testScope(playTest, scalatestPlus, playLogback, kamonTestkit))

lazy val testsOnPlay26 = Project("tests-26", file("tests-2.6"))
  .dependsOn(instrumentation)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(noPublishing: _*)
  .settings(
    name := "tests-2.6",
    scalaVersion := "2.12.8",
    crossScalaVersions := Seq("2.11.12", "2.12.8"),
    testGrouping in Test := singleTestPerJvm((definedTests in Test).value, (javaOptions in Test).value),
    unmanagedSourceDirectories in Test ++= (unmanagedSourceDirectories in Test in commonTests).value,
    unmanagedResourceDirectories in Test ++= (unmanagedResourceDirectories in Test in commonTests).value,
    libraryDependencies ++=
      compileScope(kamonCore, kamonScala, kamonAkkaHttp, kamonCommon) ++
      providedScope(kanelaAgent) ++
      providedScope(onPlay26(play, playNetty, playAkkaHttp, playWS): _*) ++
      testScope(onPlay26(playTest, playLogback): _*) ++
      testScope(scalatestPlus, kamonTestkit))

lazy val testsOnPlay27 = Project("tests-27", file("tests-2.7"))
  .dependsOn(instrumentation)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(noPublishing: _*)
  .settings(
    name := "tests-2.7",
    scalaVersion := "2.12.8",
    crossScalaVersions := Seq("2.12.8", "2.13.0"),
    testGrouping in Test := singleTestPerJvm((definedTests in Test).value, (javaOptions in Test).value),
    unmanagedSourceDirectories in Test ++= (unmanagedSourceDirectories in Test in commonTests).value,
    unmanagedResourceDirectories in Test ++= (unmanagedResourceDirectories in Test in commonTests).value,
    libraryDependencies ++=
      compileScope(kamonCore, kamonScala, kamonAkkaHttp, kamonCommon) ++
      providedScope(play, playNetty, playAkkaHttp, playWS, kanelaAgent) ++
      testScope(playTest, scalatestPlus, playLogback, kamonTestkit))


import sbt.Tests._
def singleTestPerJvm(tests: Seq[TestDefinition], jvmSettings: Seq[String]): Seq[Group] =
  tests map { test =>
    Group(
      name = test.name,
      tests = Seq(test),
      runPolicy = SubProcess(ForkOptions(
        javaHome = Option.empty[File],
        outputStrategy = Option.empty[OutputStrategy],
        bootJars = Vector(),
        workingDirectory = Option.empty[File],
        runJVMOptions = jvmSettings.toVector,
        connectInput = false,
        envVars = Map.empty[String, String])
      )
    )
  }

def onPlay26(modules: ModuleID*): Seq[ModuleID] =
  modules.map(_.withRevision(play26Version))