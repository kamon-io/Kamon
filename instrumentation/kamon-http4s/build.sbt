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
  "org.http4s" %% "http4s-client" % "0.23.19" % "http4s-0.23,provided",
  "org.http4s" %% "http4s-server" % "0.23.19" % "http4s-0.23,provided",
  "org.http4s" %% "http4s-blaze-client" % "0.23.14" % `Test-Http4s-0.23`,
  "org.http4s" %% "http4s-blaze-server" % "0.23.14" % `Test-Http4s-0.23`,
  "org.http4s" %% "http4s-dsl" % "0.23.19" % `Test-Http4s-0.23`,
  scalatestLocal % `Test-Http4s-0.23`
)

val `http4s_1.0_deps` = List(
  "org.http4s" %% "http4s-client" % "1.0.0-M38" % "http4s-1.0,provided",
  "org.http4s" %% "http4s-server" % "1.0.0-M38" % "http4s-1.0,provided",
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

// Ensure that the packaged sources contains the instrumentation for all Http4s versions.
Compile / packageSrc / mappings := Def.taskDyn {
  if(scalaBinaryVersion.value == "2.12") {
    Def.task {
      (`Compile-Http4s-0.23` / packageSrc / mappings).value ++
        (Common / packageSrc / mappings).value
    }
  } else {
    Def.task {
      (`Compile-Http4s-0.23` / packageSrc / mappings).value ++
        (`Compile-Http4s-1.0` / packageSrc / mappings).value ++
        (Common / packageSrc / mappings).value
    }
  }
}.value

exportJars := true

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