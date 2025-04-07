import sbt.Tests._

val `Play-2.7-version` = "2.7.9"
val `Play-2.8-version` = "2.8.2"

/**
  * Test Configurations
  */
lazy val TestCommon = config("test-common") extend (Compile)
lazy val `Test-Play-2.7` = config("test-play-2.7")
lazy val `Test-Play-2.8` = config("test-play-2.8")

configs(
  TestCommon,
  `Test-Play-2.8`,
  `Test-Play-2.7`
)

libraryDependencies ++= Seq(
  kanelaAgent % "provided",
  "com.typesafe.play" %% "play" % `Play-2.7-version` % "provided,test-common,test-play-2.7",
  "com.typesafe.play" %% "play-netty-server" % `Play-2.7-version` % "provided,test-common,test-play-2.7",
  "com.typesafe.play" %% "play-akka-http-server" % `Play-2.7-version` % "provided,test-common,test-play-2.7",
  "com.typesafe.play" %% "play-ws" % `Play-2.7-version` % "provided,test-common,test-play-2.7",
  "com.typesafe.play" %% "play-test" % `Play-2.7-version` % "provided,test-common,test-play-2.7",
  "com.typesafe.play" %% "play-logback" % `Play-2.7-version` % "test-common,test-play-2.7",
  scalatest % "test-common,test-play-2.8,test-play-2.7",
  "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % "test-play-2.8,test-play-2.7"
)

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-akka-http2-support" % `Play-2.8-version` % "test-play-2.8",
  "com.typesafe.play" %% "play" % `Play-2.8-version` % "test-play-2.8",
  "com.typesafe.play" %% "play-netty-server" % `Play-2.8-version` % "test-play-2.8",
  "com.typesafe.play" %% "play-akka-http-server" % `Play-2.8-version` % "test-play-2.8",
  "com.typesafe.play" %% "play-ws" % `Play-2.8-version` % "test-play-2.8",
  "com.typesafe.play" %% "play-test" % `Play-2.8-version` % "test-play-2.8",
  "com.typesafe.play" %% "play-logback" % `Play-2.8-version` % "test-play-2.8"
)

/**
  * Test-related settings
  */

lazy val baseTestSettings = Seq(
  fork := true,
  parallelExecution := false,
  javaOptions := (Test / javaOptions).value ++ Seq("-Dkanela.loglevel=DEBUG", "-Dkanela.debug-mode=yes"),
  dependencyClasspath += (Compile / packageBin).value
)

inConfig(TestCommon)(Defaults.testSettings ++ instrumentationSettings ++ baseTestSettings ++ Seq(
  crossScalaVersions := Seq(`scala_2.13_version`)
))

inConfig(`Test-Play-2.7`)(Defaults.testSettings ++ instrumentationSettings ++ baseTestSettings ++ Seq(
  sources := joinSources(TestCommon, `Test-Play-2.7`).value,
  testGrouping := singleTestPerJvm(definedTests.value, javaOptions.value),
  unmanagedResourceDirectories ++= (Compile / unmanagedResourceDirectories).value,
  unmanagedResourceDirectories ++= (TestCommon / unmanagedResourceDirectories).value
))

inConfig(`Test-Play-2.8`)(Defaults.testSettings ++ instrumentationSettings ++ baseTestSettings ++ Seq(
  sources := joinSources(TestCommon, `Test-Play-2.8`).value,
  crossScalaVersions := Seq(`scala_2.13_version`),
  testGrouping := singleTestPerJvm(definedTests.value, javaOptions.value),
  unmanagedResourceDirectories ++= (Compile / unmanagedResourceDirectories).value,
  unmanagedResourceDirectories ++= (TestCommon / unmanagedResourceDirectories).value
))

Test / test := Def.taskDyn {
  Def.task {
    (`Test-Play-2.7` / test).value
    (`Test-Play-2.8` / test).value
  }
}.value

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
        envVars = Map.empty[String, String]
      ))
    )
  }
