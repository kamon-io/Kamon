import sbt.Tests.{Group, SubProcess}
import Def.Initialize

val `Akka-2.4-version` = "2.4.20"
val `Akka-2.5-version` = "2.5.32"
val `Akka-2.6-version` = "2.6.21"

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
  crossScalaVersions := Seq(`scala_2.12_version`, `scala_2.13_version`, scala_3_version)
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
  crossScalaVersions := Seq(`scala_2.12_version`, `scala_2.13_version`, scala_3_version),
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

libraryDependencies ++= {if (scalaVersion.value startsWith "3") Seq.empty else Seq(
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
)}

// Ensure that the packaged artifact contains the instrumentation for all Akka versions.
Compile / packageBin / mappings := Def.taskDyn {
  if(scalaBinaryVersion.value == "2.11") {
    Def.task {
      joinProducts((`Compile-Akka-2.5` / products).value) ++
      joinProducts((Common / unmanagedResourceDirectories).value)
    }
  } else if (scalaVersion.value startsWith "3") {
    Def.task {
      joinProducts((`Compile-Akka-2.6` / products).value) ++ 
      joinProducts((Common / unmanagedResourceDirectories).value)
    }
  } else {
    Def.task {
      joinProducts(
        (`Compile-Akka-2.5` / products).value ++
        (`Compile-Akka-2.6` / products).value
        ) ++ joinProducts((Common / unmanagedResourceDirectories).value)
    }
  }
}.value

// Ensure that the packaged sources contains the instrumentation for all Akka versions.
Compile / packageSrc / mappings := Def.taskDyn {
  if(scalaBinaryVersion.value == "2.11") {
    Def.task {
      (`Compile-Akka-2.5` / packageSrc / mappings).value ++
      (Common / packageSrc / mappings).value
    } 
  } else if (scalaVersion.value startsWith "3") {
    Def.task {
      (`Compile-Akka-2.6`  / packageSrc / mappings).value ++
      (Common / packageSrc / mappings).value
    }
  } else {
    Def.task {
      (`Compile-Akka-2.5` / packageSrc / mappings).value ++
      (`Compile-Akka-2.6` / packageSrc / mappings).value ++
      (Common / packageSrc / mappings).value
    }
  }
  }.value

// Compile will return the compile analysis for the Common configuration but will run on all Akka configurations.
Compile / compile := Def.taskDyn {
  if(scalaBinaryVersion.value == "2.11") {
    Def.task {
      (`Compile-Akka-2.5` / compile).value
    }
  } else if (scalaVersion.value startsWith "3"){

    Def.task {
      (`Compile-Akka-2.6` / compile).value
    } 
  } else {
    Def.task {
      (`Compile-Akka-2.5` / compile).value
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
  crossScalaVersions := Seq(`scala_2.12_version`, `scala_2.13_version`, scala_3_version)
))

inConfig(`Test-Akka-2.5`)(Defaults.testSettings ++ instrumentationSettings ++ baseTestSettings ++ Seq(
  sources := joinSources(TestCommon, `Test-Akka-2.5`).value,
  unmanagedResourceDirectories ++= (Common / unmanagedResourceDirectories).value,
  unmanagedResourceDirectories ++= (TestCommon / unmanagedResourceDirectories).value
))

inConfig(`Test-Akka-2.6`)(Defaults.testSettings ++ instrumentationSettings ++ baseTestSettings ++ Seq(
  crossScalaVersions := Seq(`scala_2.12_version`, `scala_2.13_version`, scala_3_version),
  sources := joinSources(TestCommon, `Test-Akka-2.6`).value,
  unmanagedResourceDirectories ++= (Common / unmanagedResourceDirectories).value,
  unmanagedResourceDirectories ++= (TestCommon / unmanagedResourceDirectories).value
))

Test / test := Def.taskDyn {
  if(scalaBinaryVersion.value == "2.11") {
    Def.task {
      (`Test-Akka-2.5` / test).value
    }
  } else if (scalaVersion.value startsWith "3") {
    Def.task {
      (`Test-Akka-2.6` / test).value
    }
  }
  else {
    Def.task {
      (`Test-Akka-2.5` / test).value
      (`Test-Akka-2.6` / test).value
    }
  }
}.value