resolvers += "Apache OSS Snapshots" at "https://repository.apache.org/content/repositories/snapshots"

// The Common configuration should always depend on the latest version of Akka. All code in the Common configuration
// should be source compatible with all Akka versions.
inConfig(Compile)(Defaults.compileSettings ++ Seq(
  crossScalaVersions := Seq(`scala_2.12_version`, `scala_2.13_version`)
))

val pekkoVersion = "0.0.0+26601-29800df0-SNAPSHOT"
libraryDependencies ++= { if(scalaBinaryVersion.value == "2.11") Seq.empty else Seq(
  kanelaAgent,
  scalatest % Test,
  logbackClassic % Test,
  "org.apache.pekko"   %% "pekko-actor"             % pekkoVersion,
  "org.apache.pekko"   %% "pekko-testkit"           % pekkoVersion,
  "org.apache.pekko"   %% "pekko-slf4j"             % pekkoVersion,
  "org.apache.pekko"   %% "pekko-remote"            % pekkoVersion,
  "org.apache.pekko"   %% "pekko-cluster"           % pekkoVersion,
  "org.apache.pekko"   %% "pekko-cluster-sharding"  % pekkoVersion,
  "org.apache.pekko"   %% "pekko-protobuf"          % pekkoVersion,
  "org.apache.pekko"   %% "pekko-testkit"           % pekkoVersion % Test
)}

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

inConfig(Test)(Defaults.testSettings ++ instrumentationSettings ++ baseTestSettings ++ Seq(
  crossScalaVersions := Seq(`scala_2.12_version`, `scala_2.13_version`)
))
