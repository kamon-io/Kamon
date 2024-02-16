/* =========================================================================================
 * Copyright © 2013-2019 the kamon project <http://kamon.io/>
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

lazy val `Compile-Http4s-0.23`: sbt.Configuration = config("http4s-0.23")
lazy val `Compile-Http4s-1.0` = config("http4s-1.0")
lazy val Common = config("common")

lazy val TestCommon = config("test-common") extend(Common)
lazy val `Test-Http4s-0.23` = config("test-http4s-0.23") extend(`Compile-Http4s-0.23`)
lazy val `Test-Http4s-1.0` = config("test-http4s-1.0") extend(`Compile-Http4s-1.0`)

configs(
  Common,
  `Compile-Http4s-0.23`,
  `Compile-Http4s-1.0`,
  TestCommon,
  `Test-Http4s-0.23`,
  `Test-Http4s-1.0`
)

val scalatestLocal = "org.scalatest" %% "scalatest" % "3.2.15"

val `http4s_0.23_deps` = List(
  "org.http4s" %% "http4s-client" % "0.23.19" % `Compile-Http4s-0.23`,
  "org.http4s" %% "http4s-server" % "0.23.19" % `Compile-Http4s-0.23`,
  "org.http4s" %% "http4s-blaze-client" % "0.23.14" % `Test-Http4s-0.23`,
  "org.http4s" %% "http4s-blaze-server" % "0.23.14" % `Test-Http4s-0.23`,
  "org.http4s" %% "http4s-dsl" % "0.23.19" % `Test-Http4s-0.23`,
  scalatestLocal % `Test-Http4s-0.23`
)

val `http4s_1.0_deps` = List(
  "org.http4s" %% "http4s-client" % "1.0.0-M38" % `Compile-Http4s-1.0`,
  "org.http4s" %% "http4s-server" % "1.0.0-M38" % `Compile-Http4s-1.0`,
  "org.http4s" %% "http4s-blaze-client" % "1.0.0-M38" % `Test-Http4s-1.0`,
  "org.http4s" %% "http4s-blaze-server" % "1.0.0-M38" % `Test-Http4s-1.0`,
  "org.http4s" %% "http4s-dsl" % "1.0.0-M38" % `Test-Http4s-1.0`,
  scalatestLocal % `Test-Http4s-1.0`
)

inConfig(Common)(Defaults.compileSettings ++ Seq(
  crossScalaVersions := Seq(`scala_2.12_version`, `scala_2.13_version`, scala_3_version)
))

libraryDependencies ++= `http4s_0.23_deps`

inConfig(`Compile-Http4s-0.23`)(Defaults.compileSettings ++ Seq(
  crossScalaVersions := Seq(`scala_2.12_version`, `scala_2.13_version`, scala_3_version),
  sources := joinSources(Common, `Compile-Http4s-0.23`).value,
))

libraryDependencies ++= { if(scalaBinaryVersion.value == "2.12") Seq.empty else `http4s_1.0_deps` }

inConfig(`Compile-Http4s-1.0`)(Defaults.compileSettings ++ Seq(
  crossScalaVersions := Seq(`scala_2.13_version`, scala_3_version),
  sources := joinSources(Common, `Compile-Http4s-1.0`).value,
))

// Compile will return the compile analysis for the Common configuration but will run on all Http4s configurations.
Compile / compile := Def.taskDyn {
  if(scalaBinaryVersion.value == "2.12") {
    Def.task {
      (`Compile-Http4s-0.23` / compile).value
    }
  }
  else {
    Def.task {
      (`Compile-Http4s-0.23` / compile).value
      (`Compile-Http4s-1.0` / compile).value
    }
  }
}.value

/**
 * Test-related settings
 */

lazy val baseTestSettings = Seq(
  fork := true,
  parallelExecution := false,
  javaOptions := (Test / javaOptions).value,
  dependencyClasspath += (Compile / packageBin).value
)

inConfig(TestCommon)(Defaults.testSettings ++ instrumentationSettings ++ baseTestSettings ++ Seq(
  crossScalaVersions := Seq(`scala_2.12_version`, `scala_2.13_version`, scala_3_version)
))

inConfig(`Test-Http4s-0.23`)(Defaults.testSettings ++ instrumentationSettings ++ baseTestSettings ++ Seq(
  crossScalaVersions := Seq(`scala_2.12_version`, `scala_2.13_version`, scala_3_version),
  sources := joinSources(TestCommon, `Test-Http4s-0.23`).value,
  unmanagedResourceDirectories ++= (Common / unmanagedResourceDirectories).value,
  unmanagedResourceDirectories ++= (TestCommon / unmanagedResourceDirectories).value
))

inConfig(`Test-Http4s-1.0`)(Defaults.testSettings ++ instrumentationSettings ++ baseTestSettings ++ Seq(
  crossScalaVersions := Seq(`scala_2.13_version`, scala_3_version),
  sources := joinSources(TestCommon, `Test-Http4s-1.0`).value,
  unmanagedResourceDirectories ++= (Common / unmanagedResourceDirectories).value,
  unmanagedResourceDirectories ++= (TestCommon / unmanagedResourceDirectories).value
))

Test / test := Def.taskDyn {
  if (scalaBinaryVersion.value == "2.12") {
    Def.task {
      (`Test-Http4s-0.23` / test).value
    }
  }
  else {
    Def.task {
      (`Test-Http4s-0.23` / test).value
      (`Test-Http4s-1.0` / test).value
    }
  }
}.value

//lazy val shared = Seq(
//  organization := "io.kamon",
//  scalaVersion := "2.12.14",
//  crossScalaVersions := Seq("2.12.14", "2.13.8"),
//  moduleName := name.value,
//  publishTo := sonatypePublishToBundle.value,
//  scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
//    case Some((2, 12)) => Seq("-Ypartial-unification", "-language:higherKinds")
//    case Some((3, _))  => Seq("-source:3.0-migration", "-Xtarget:8")
//    case _             => "-language:higherKinds" :: Nil
//  }),
//  libraryDependencies ++= Seq(kamonCore, kamonCommon) ++ Seq(
//    scalatestLocal,
//    kamonTestkit
//  ).map(_ % Test),
//  publishMavenStyle := true,
//  licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
//  homepage := Some(url("https://kamon.io")),
//  scmInfo := Some(
//    ScmInfo(
//      url("https://github.com/kamon-io/kamon-http4s"),
//      "scm:git@github.com:kamon-io/kamon-http4s.git"
//    )
//  ),
//  developers := List(
//    Developer(id="ivantopo", name="Ivan Topolnjak", url=url("https://twitter.com/ivantopo"), email=""),
//    Developer(id="dpsoft", name="Diego Parra", url=url("https://twitter.com/dpsoft"), email=""),
//    Developer(id="vaslabs", name="Vasilis Nicolaou", url=url("https://github.com/vaslabs"), email=""),
//    Developer(id="jchapuis", name="Jonas Chapuis", url=url("https://github.com/jchapuis"), email="")
//  )
//)

//
//lazy val `kamon-http4s-0_23` = project
//  .in(file("0.23"))
//  .settings(
//    shared,
//    name := "kamon-http4s-0.23",
//    crossScalaVersions += "3.3.0",
//    libraryDependencies ++= http4sDeps("0.23.19", "0.23.14")
//  )
//
//lazy val `kamon-http4s-1_0` = project
//  .in(file("1.0"))
//  .settings(
//    shared,
//    name := "kamon-http4s-1.0",
//    crossScalaVersions := Seq("2.13.8", "3.3.0"),
//    libraryDependencies ++= http4sDeps("1.0.0-M38", "1.0.0-M38")
//  )
//
//lazy val root = project
//  .in(file("."))
//  .settings(
//    shared,
//    name := "kamon-http4s",
//    publish / skip := true,
//    Test / parallelExecution := false,
//    Global / concurrentRestrictions += Tags.limit(Tags.Test, 1)
//  )
//  .aggregate(`kamon-http4s-0_23`, `kamon-http4s-1_0`)
