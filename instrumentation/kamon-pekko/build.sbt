// The Common configuration should always depend on the latest version of Pekko. All code in the Common configuration
// should be source compatible with all Pekko versions.
inConfig(Compile)(Defaults.compileSettings ++ Seq(
  crossScalaVersions := Seq(`scala_2.12_version`, `scala_2.13_version`, scala_3_version)
))
lazy val Pekko1_0Test = config("test-pekko-1.0") extend (Test)
lazy val Pekko1_1Test = config("test-pekko-1.1") extend (Test)

val pekko1_0_Version = "1.0.3"
val pekko1_1_Version = "1.1.1"
libraryDependencies ++= {
  if (scalaBinaryVersion.value == "2.11") Seq.empty
  else Seq(
    kanelaAgent % "provided,test,test-pekko-1.0,test-pekko-1.1",
    scalatest % "test,test-pekko-1.0,test-pekko-1.1",
    logbackClassic % "test,test-pekko-1.0,test-pekko-1.1",
    "org.apache.pekko" %% "pekko-actor" % pekko1_0_Version % "provided,test-pekko-1.0",
    "org.apache.pekko" %% "pekko-testkit" % pekko1_0_Version % "provided,test-pekko-1.0",
    "org.apache.pekko" %% "pekko-slf4j" % pekko1_0_Version % "provided,test-pekko-1.0",
    "org.apache.pekko" %% "pekko-remote" % pekko1_0_Version % "provided,test-pekko-1.0",
    "org.apache.pekko" %% "pekko-cluster" % pekko1_0_Version % "provided,test-pekko-1.0",
    "org.apache.pekko" %% "pekko-cluster-sharding" % pekko1_0_Version % "provided,test-pekko-1.0",
    "org.apache.pekko" %% "pekko-protobuf-v3" % pekko1_0_Version % "provided,test-pekko-1.0",
    "org.apache.pekko" %% "pekko-testkit" % pekko1_0_Version % "test-pekko-1.0",
    "org.apache.pekko" %% "pekko-actor" % pekko1_1_Version % "provided,test-pekko-1.1",
    "org.apache.pekko" %% "pekko-testkit" % pekko1_1_Version % "provided,test-pekko-1.1",
    "org.apache.pekko" %% "pekko-slf4j" % pekko1_1_Version % "provided,test-pekko-1.1",
    "org.apache.pekko" %% "pekko-remote" % pekko1_1_Version % "provided,test-pekko-1.1",
    "org.apache.pekko" %% "pekko-cluster" % pekko1_1_Version % "provided,test-pekko-1.1",
    "org.apache.pekko" %% "pekko-cluster-sharding" % pekko1_1_Version % "provided,test-pekko-1.1",
    "org.apache.pekko" %% "pekko-protobuf-v3" % pekko1_1_Version % "provided,test-pekko-1.1",
    "org.apache.pekko" %% "pekko-testkit" % pekko1_1_Version % "test-pekko-1.1"
  )
}

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
  crossScalaVersions := Seq(`scala_2.12_version`, `scala_2.13_version`, scala_3_version)
))

configs(Pekko1_0Test, Pekko1_1Test)
inConfig(Pekko1_0Test)(Defaults.testSettings ++ Seq(
  sources := (Test / sources).value,
  unmanagedResourceDirectories ++= (Compile / unmanagedResourceDirectories).value,
  unmanagedResourceDirectories ++= (Test / unmanagedResourceDirectories).value
))
inConfig(Pekko1_1Test)(Defaults.testSettings ++ Seq(
  sources := (Test / sources).value,
  unmanagedResourceDirectories ++= (Compile / unmanagedResourceDirectories).value,
  unmanagedResourceDirectories ++= (Test / unmanagedResourceDirectories).value
))

Test / test := {
  (Pekko1_0Test / test).value
  (Pekko1_1Test / test).value
}
Test / testOnly := {
  (Pekko1_0Test / testOnly).evaluated

  (Pekko1_1Test / testOnly).evaluated
}
