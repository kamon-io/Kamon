import sbt.Tests.{Group, SubProcess}
import Def.Initialize

val `Akka-2.4-version` = "2.4.20"
val `Akka-2.5-version` = "2.5.26"
val `Akka-2.6-version` = "2.6.11"

/**
  * Compile Configurations
  */
lazy val Common = config("common")
lazy val `Compile-Akka-2.5` = config("akka-2.5")
lazy val `Compile-Akka-2.6` = config("akka-2.6")

/**
  * Test Configurations
  */
lazy val TestCommon = config("test-common") extend(Common)
lazy val `Test-Akka-2.5` = config("test-akka-2.5") extend(`Compile-Akka-2.5`)
lazy val `Test-Akka-2.6` = config("test-akka-2.6") extend(`Compile-Akka-2.6`)

configs(
  Common,
  `Compile-Akka-2.5`,
  `Compile-Akka-2.6`,
  TestCommon,
  `Test-Akka-2.5`,
  `Test-Akka-2.6`
)

// The Common configuration should always depend on the latest version of Akka. All code in the Common configuration
// should be source compatible with all Akka versions.
inConfig(Common)(Defaults.compileSettings ++ Seq(
  crossScalaVersions := Seq("2.12.11", "2.13.1")
))

libraryDependencies ++= { if(scalaBinaryVersion.value == "2.11") Seq.empty else Seq(
  kanelaAgent % Common,
  scalatest % TestCommon,
  logbackClassic % TestCommon,
  "com.typesafe.akka"   %% "akka-actor"             % `Akka-2.6-version` % Common,
  "com.typesafe.akka"   %% "akka-testkit"           % `Akka-2.6-version` % Common,
  "com.typesafe.akka"   %% "akka-slf4j"             % `Akka-2.6-version` % Common,
  "com.typesafe.akka"   %% "akka-remote"            % `Akka-2.6-version` % Common,
  "com.typesafe.akka"   %% "akka-cluster"           % `Akka-2.6-version` % Common,
  "com.typesafe.akka"   %% "akka-cluster-sharding"  % `Akka-2.6-version` % Common,
  "com.typesafe.akka"   %% "akka-protobuf"          % `Akka-2.6-version` % Common,
  "com.typesafe.akka"   %% "akka-testkit"           % `Akka-2.6-version` % TestCommon
)}


inConfig(`Compile-Akka-2.6`)(Defaults.compileSettings ++ Seq(
  crossScalaVersions := Seq("2.12.11", "2.13.1"),
  sources := joinSources(Common, `Compile-Akka-2.6`).value
))

libraryDependencies ++= { if(scalaBinaryVersion.value == "2.11") Seq.empty else Seq(
  kanelaAgent % `Compile-Akka-2.6`,
  scalatest % `Test-Akka-2.6`,
  logbackClassic % `Test-Akka-2.6`,
  "com.typesafe.akka"   %% "akka-actor"             % `Akka-2.6-version` % `Compile-Akka-2.6`,
  "com.typesafe.akka"   %% "akka-testkit"           % `Akka-2.6-version` % `Compile-Akka-2.6`,
  "com.typesafe.akka"   %% "akka-slf4j"             % `Akka-2.6-version` % `Compile-Akka-2.6`,
  "com.typesafe.akka"   %% "akka-remote"            % `Akka-2.6-version` % `Compile-Akka-2.6`,
  "com.typesafe.akka"   %% "akka-cluster"           % `Akka-2.6-version` % `Compile-Akka-2.6`,
  "com.typesafe.akka"   %% "akka-cluster-sharding"  % `Akka-2.6-version` % `Compile-Akka-2.6`,
  "com.typesafe.akka"   %% "akka-protobuf"          % `Akka-2.6-version` % `Compile-Akka-2.6`,
  "com.typesafe.akka"   %% "akka-testkit"           % `Akka-2.6-version` % `Test-Akka-2.6`
)}


inConfig(`Compile-Akka-2.5`)(Defaults.compileSettings ++ Seq(
  sources := joinSources(Common, `Compile-Akka-2.5`).value
))

libraryDependencies ++= Seq(
  kanelaAgent % `Compile-Akka-2.5`,
  scalatest % `Test-Akka-2.5`,
  logbackClassic % `Test-Akka-2.5`,
  "com.typesafe.akka"   %% "akka-actor"             % `Akka-2.5-version` % `Compile-Akka-2.5`,
  "com.typesafe.akka"   %% "akka-testkit"           % `Akka-2.5-version` % `Compile-Akka-2.5`,
  "com.typesafe.akka"   %% "akka-slf4j"             % `Akka-2.5-version` % `Compile-Akka-2.5`,
  "com.typesafe.akka"   %% "akka-remote"            % `Akka-2.5-version` % `Compile-Akka-2.5`,
  "com.typesafe.akka"   %% "akka-cluster"           % `Akka-2.5-version` % `Compile-Akka-2.5`,
  "com.typesafe.akka"   %% "akka-cluster-sharding"  % `Akka-2.5-version` % `Compile-Akka-2.5`,
  "com.typesafe.akka"   %% "akka-protobuf"          % `Akka-2.5-version` % `Compile-Akka-2.5`,
  "com.typesafe.akka"   %% "akka-testkit"           % `Akka-2.5-version` % `Test-Akka-2.5`
)

// Ensure that the packaged artifact contains the instrumentation for all Akka versions.
mappings in packageBin in Compile := Def.taskDyn {
  if(scalaBinaryVersion.value == "2.11")
    Def.task {
      joinProducts((products in `Compile-Akka-2.5`).value) ++
      joinProducts((unmanagedResourceDirectories in Common).value)
    }
  else
    Def.task {
      joinProducts(
        (products in `Compile-Akka-2.5`).value ++
        (products in `Compile-Akka-2.6`).value
      ) ++ joinProducts((unmanagedResourceDirectories in Common).value)}
}.value

// Compile will return the compile analysis for the Common configuration but will run on all Akka configurations.
compile in Compile := Def.taskDyn {
  if(scalaBinaryVersion.value == "2.11")
    Def.task {
      (compile in `Compile-Akka-2.5`).value
    }
  else
    Def.task {
      (compile in `Compile-Akka-2.5`).value
      (compile in `Compile-Akka-2.6`).value
    }
}.value

exportJars := true


/**
  * Test-related settings
  */

lazy val baseTestSettings = Seq(
  fork := true,
  parallelExecution := false,
  javaOptions := (javaOptions in Test).value,
  dependencyClasspath += (packageBin in Compile).value,
)

inConfig(TestCommon)(Defaults.testSettings ++ instrumentationSettings ++ baseTestSettings ++ Seq(
  crossScalaVersions := Seq("2.12.11", "2.13.1")
))

inConfig(`Test-Akka-2.5`)(Defaults.testSettings ++ instrumentationSettings ++ baseTestSettings ++ Seq(
  sources := joinSources(TestCommon, `Test-Akka-2.5`).value,
  unmanagedResourceDirectories ++= (unmanagedResourceDirectories in Common).value,
  unmanagedResourceDirectories ++= (unmanagedResourceDirectories in TestCommon).value
))

inConfig(`Test-Akka-2.6`)(Defaults.testSettings ++ instrumentationSettings ++ baseTestSettings ++ Seq(
  crossScalaVersions := Seq("2.12.11", "2.13.1"),
  sources := joinSources(TestCommon, `Test-Akka-2.6`).value,
  unmanagedResourceDirectories ++= (unmanagedResourceDirectories in Common).value,
  unmanagedResourceDirectories ++= (unmanagedResourceDirectories in TestCommon).value
))

test in Test := Def.taskDyn {
  if(scalaBinaryVersion.value == "2.11")
    Def.task {
      (test in `Test-Akka-2.5`).value
    }
  else
    Def.task {
      (test in `Test-Akka-2.5`).value
      (test in `Test-Akka-2.6`).value
    }
}.value
