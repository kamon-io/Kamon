// The Common configuration should always depend on the latest version of Pekko. All code in the Common configuration
// should be source compatible with all Pekko versions.
inConfig(Compile)(Defaults.compileSettings ++ Seq(
  crossScalaVersions := Seq(`scala_2.12_version`, `scala_2.13_version`, scala_3_version)
))

val pekkoVersion = "1.0.1"
libraryDependencies ++= { if(scalaBinaryVersion.value == "2.11") Seq.empty else Seq(
  kanelaAgent % "provided",
  scalatest % Test,
  logbackClassic % Test,
  "org.apache.pekko"   %% "pekko-actor"             % pekkoVersion % "provided,test",
  "org.apache.pekko"   %% "pekko-testkit"           % pekkoVersion % "provided,test",
  "org.apache.pekko"   %% "pekko-slf4j"             % pekkoVersion % "provided,test",
  "org.apache.pekko"   %% "pekko-remote"            % pekkoVersion % "provided,test",
  "org.apache.pekko"   %% "pekko-cluster"           % pekkoVersion % "provided,test",
  "org.apache.pekko"   %% "pekko-cluster-sharding"  % pekkoVersion % "provided,test",
  "org.apache.pekko"   %% "pekko-protobuf"          % pekkoVersion % "provided,test",
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
  crossScalaVersions := Seq(`scala_2.12_version`, `scala_2.13_version`, scala_3_version),
))
