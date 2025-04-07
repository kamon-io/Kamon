import sbt.Tests.{Group, SubProcess}
import Def.Initialize

val `Akka-2.6-version` = "2.6.21"

/**
  * Compile Configurations
  */
lazy val Common = config("common")
lazy val `Compile-Akka-2.6` = config("akka-2.6")

/**
  * Test Configurations
  */
lazy val TestCommon = config("test-common") extend (Common)
lazy val `Test-Akka-2.6` = config("test-akka-2.6") extend (`Compile-Akka-2.6`)

configs(
  Common,
  `Compile-Akka-2.6`,
  TestCommon,
  `Test-Akka-2.6`
)

// The Common configuration should always depend on the latest version of Akka. All code in the Common configuration
// should be source compatible with all Akka versions.
inConfig(Common)(Defaults.compileSettings ++ Seq(
  crossScalaVersions := Seq(`scala_2.13_version`, scala_3_version)
))

libraryDependencies ++= Seq(
  kanelaAgent % Common,
  scalatest % TestCommon,
  logbackClassic % TestCommon,
  "com.typesafe.akka" %% "akka-actor" % `Akka-2.6-version` % Common,
  "com.typesafe.akka" %% "akka-testkit" % `Akka-2.6-version` % Common,
  "com.typesafe.akka" %% "akka-slf4j" % `Akka-2.6-version` % Common,
  "com.typesafe.akka" %% "akka-remote" % `Akka-2.6-version` % Common,
  "com.typesafe.akka" %% "akka-cluster" % `Akka-2.6-version` % Common,
  "com.typesafe.akka" %% "akka-cluster-sharding" % `Akka-2.6-version` % Common,
  "com.typesafe.akka" %% "akka-protobuf" % `Akka-2.6-version` % Common,
  "com.typesafe.akka" %% "akka-testkit" % `Akka-2.6-version` % TestCommon
)

inConfig(`Compile-Akka-2.6`)(Defaults.compileSettings ++ Seq(
  crossScalaVersions := Seq(`scala_2.13_version`, scala_3_version),
  sources := joinSources(Common, `Compile-Akka-2.6`).value
))

libraryDependencies ++= Seq(
  kanelaAgent % `Compile-Akka-2.6`,
  scalatest % `Test-Akka-2.6`,
  logbackClassic % `Test-Akka-2.6`,
  "com.typesafe.akka" %% "akka-actor" % `Akka-2.6-version` % `Compile-Akka-2.6`,
  "com.typesafe.akka" %% "akka-testkit" % `Akka-2.6-version` % `Compile-Akka-2.6`,
  "com.typesafe.akka" %% "akka-slf4j" % `Akka-2.6-version` % `Compile-Akka-2.6`,
  "com.typesafe.akka" %% "akka-remote" % `Akka-2.6-version` % `Compile-Akka-2.6`,
  "com.typesafe.akka" %% "akka-cluster" % `Akka-2.6-version` % `Compile-Akka-2.6`,
  "com.typesafe.akka" %% "akka-cluster-sharding" % `Akka-2.6-version` % `Compile-Akka-2.6`,
  "com.typesafe.akka" %% "akka-protobuf" % `Akka-2.6-version` % `Compile-Akka-2.6`,
  "com.typesafe.akka" %% "akka-testkit" % `Akka-2.6-version` % `Test-Akka-2.6`
)

// Ensure that the packaged artifact contains the instrumentation for all Akka versions.
Compile / packageBin / mappings := Def.taskDyn {
  if (scalaVersion.value startsWith "3") {
    Def.task {
      joinProducts((`Compile-Akka-2.6` / products).value) ++
      joinProducts((Common / unmanagedResourceDirectories).value)
    }
  } else {
    Def.task {
      joinProducts(
        (`Compile-Akka-2.6` / products).value
      ) ++ joinProducts((Common / unmanagedResourceDirectories).value)
    }
  }
}.value

// Ensure that the packaged sources contains the instrumentation for all Akka versions.
Compile / packageSrc / mappings := Def.taskDyn {
  if (scalaVersion.value startsWith "3") {
    Def.task {
      (`Compile-Akka-2.6` / packageSrc / mappings).value ++
      (Common / packageSrc / mappings).value
    }
  } else {
    Def.task {
      (`Compile-Akka-2.6` / packageSrc / mappings).value ++
      (Common / packageSrc / mappings).value
    }
  }
}.value

// Compile will return the compile analysis for the Common configuration but will run on all Akka configurations.
Compile / compile := Def.taskDyn {
  if (scalaVersion.value startsWith "3") {
    Def.task {
      (`Compile-Akka-2.6` / compile).value
    }
  } else {
    Def.task {
      (`Compile-Akka-2.6` / compile).value
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
  crossScalaVersions := Seq(`scala_2.13_version`, scala_3_version)
))

inConfig(`Test-Akka-2.6`)(Defaults.testSettings ++ instrumentationSettings ++ baseTestSettings ++ Seq(
  crossScalaVersions := Seq(`scala_2.13_version`, scala_3_version),
  sources := joinSources(TestCommon, `Test-Akka-2.6`).value,
  unmanagedResourceDirectories ++= (Common / unmanagedResourceDirectories).value,
  unmanagedResourceDirectories ++= (TestCommon / unmanagedResourceDirectories).value
))

Test / test := Def.taskDyn {
  if (scalaVersion.value startsWith "3") {
    Def.task {
      (`Test-Akka-2.6` / test).value
    }
  } else {
    Def.task {
      (`Test-Akka-2.6` / test).value
    }
  }
}.value
