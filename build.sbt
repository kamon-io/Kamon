/*
 * =========================================================================================
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

val kamonTestKit        = "io.kamon" %% "kamon-testkit"         % "1.0.0-RC7"
val kamonAkka24         = "io.kamon" %% "kamon-akka-2.4"        % "1.0.0-RC7"
val kamonAkka25         = "io.kamon" %% "kamon-akka-2.5"        % "1.0.0-RC7"

val http24         = "com.typesafe.akka" %% "akka-http"          % "10.0.10"
val httpTestKit24  = "com.typesafe.akka" %% "akka-http-testkit"  % "10.0.10"

val http25         = "com.typesafe.akka" %% "akka-http"          % "10.0.11"
val httpTestKit25  = "com.typesafe.akka" %% "akka-http-testkit"  % "10.0.11"


lazy val baseSettings = Seq(
  scalaSource in Compile := baseDirectory.value / ".." / ".." / "kamon-akka-http"/ "src" / "main" / "scala",
  scalaSource in Test    := baseDirectory.value / ".." / ".." / "kamon-akka-http"/ "src" / "test" / "scala",
  unmanagedClasspath in Test ++= Seq(
    baseDirectory.value / ".." / ".." / "kamon-akka-http" / "src" / "main" / "resources",
    baseDirectory.value / ".." / ".." / "kamon-akka-http" / "src" / "test" / "resources"
  )
)

lazy val root = (project in file("."))
  .aggregate(kamonAkkaHttp24, kamonAkkaHttp25, kamonAkkaHttpPlayground)
  .settings(noPublishing: _*)
  .settings(Seq(crossScalaVersions := Seq("2.11.8", "2.12.1")))

lazy val kamonAkkaHttp24 = Project("kamon-akka-http-24", file("target/kamon-akka-http-24"))
  .settings(name := "kamon-akka-http-24")
  .settings(aspectJSettings: _*)
  .settings(baseSettings: _*)
  .settings(Seq(
    scalaVersion := "2.12.1",
    crossScalaVersions := Seq("2.11.8", "2.12.1"),
    testGrouping in Test := singleTestPerJvm((definedTests in Test).value, (javaOptions in Test).value)))
  .settings(libraryDependencies ++=
    compileScope(http24, kamonAkka24) ++
      testScope(httpTestKit24, scalatest, slf4jApi, slf4jnop, kamonTestKit) ++
      providedScope(aspectJ))

lazy val kamonAkkaHttp25 = Project("kamon-akka-http-25", file("target/kamon-akka-http-25"))
  .settings(name := "kamon-akka-http-25")
  .settings(aspectJSettings: _*)
  .settings(baseSettings: _*)
  .settings(Seq(
    scalaVersion := "2.12.1",
    crossScalaVersions := Seq("2.11.8", "2.12.1"),
    testGrouping in Test := singleTestPerJvm((definedTests in Test).value, (javaOptions in Test).value)))
  .settings(libraryDependencies ++=
    compileScope(http25, kamonAkka25) ++
      testScope(httpTestKit25, scalatest, slf4jApi, slf4jnop, kamonTestKit) ++
      providedScope(aspectJ))

lazy val kamonAkkaHttpPlayground = Project("kamon-akka-http-playground", file("kamon-akka-http-playground"))
  .dependsOn(kamonAkkaHttp25)
  .settings(Seq(
    scalaVersion := "2.12.1",
    crossScalaVersions := Seq("2.11.8", "2.12.1")))
  .settings(noPublishing: _*)
  .settings(settingsForPlayground: _*)
  .settings(libraryDependencies ++=
    compileScope(http25) ++
      testScope(httpTestKit25, scalatest, slf4jApi, slf4jnop) ++
      providedScope(aspectJ))

lazy val settingsForPlayground: Seq[Setting[_]] = Seq(
  connectInput in run := true,
  cancelable in Global := true
)

import sbt.Tests._
def singleTestPerJvm(tests: Seq[TestDefinition], jvmSettings: Seq[String]): Seq[Group] =
  tests map { test =>
    Group(
      name = test.name,
      tests = Seq(test),
      runPolicy = SubProcess(ForkOptions(runJVMOptions = jvmSettings)))
  }
