import sbt.Tests._

val `Play-2.8-version` = "2.8.22"
val `Play-2.9-version` = "2.9.7"
val `Play-3.0-version` = "3.0.7"

/**
  * Test Configurations
  */
lazy val TestCommon = config("test-common") extend (Compile)
lazy val `Test-Play-28` = config("test-play-28")
lazy val `Test-Play-29` = config("test-play-29")
lazy val `Test-Play-30` = config("test-play-30")

configs(
  TestCommon,
  `Test-Play-28`,
  `Test-Play-29`,
  `Test-Play-30`
)

libraryDependencies ++= Seq(
  kanelaAgent % "provided",
  scalatest % "test-common,test-play-28,test-play-29,test-play-30"
)

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-akka-http2-support" % `Play-2.8-version` % "provided,test-common,test-play-28",
  "com.typesafe.play" %% "play" % `Play-2.8-version` % "provided,test-common,test-play-28",
  "com.typesafe.play" %% "play-netty-server" % `Play-2.8-version` % "provided,test-common,test-play-28",
  "com.typesafe.play" %% "play-akka-http-server" % `Play-2.8-version` % "provided,test-common,test-play-28",
  "com.typesafe.play" %% "play-ws" % `Play-2.8-version` % "provided,test-common,test-play-28",
  "com.typesafe.play" %% "play-test" % `Play-2.8-version` % "provided,test-common,test-play-28",
  "com.typesafe.play" %% "play-logback" % `Play-2.8-version` % "provided,test-common,test-play-28",
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % "provided,test-common,test-play-28",
  "com.google.inject" % "guice" % "5.1.0" % "test-play-28",
  "com.google.inject.extensions" % "guice-assistedinject" % "5.1.0" % "test-play-28"
)

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-akka-http2-support" % `Play-2.9-version` % "test-play-29",
  "com.typesafe.play" %% "play" % `Play-2.9-version` % "test-play-29",
  "com.typesafe.play" %% "play-netty-server" % `Play-2.9-version` % "test-play-29",
  "com.typesafe.play" %% "play-akka-http-server" % `Play-2.9-version` % "test-play-29",
  "com.typesafe.play" %% "play-ws" % `Play-2.9-version` % "test-play-29",
  "com.typesafe.play" %% "play-test" % `Play-2.9-version` % "test-play-29",
  "com.typesafe.play" %% "play-logback" % `Play-2.9-version` % "test-play-29",
  "org.scalatestplus.play" %% "scalatestplus-play" % "6.0.1" % "test-play-29"
)

libraryDependencies ++= Seq(
  "org.playframework" %% "play-pekko-http2-support" % `Play-3.0-version` % "test-play-30",
  "org.playframework" %% "play" % `Play-3.0-version` % "test-play-30",
  "org.playframework" %% "play-netty-server" % `Play-3.0-version` % "test-play-30",
  "org.playframework" %% "play-pekko-http-server" % `Play-3.0-version` % "test-play-30",
  "org.playframework" %% "play-ws" % `Play-3.0-version` % "test-play-30",
  "org.playframework" %% "play-test" % `Play-3.0-version` % "test-play-30",
  "org.playframework" %% "play-logback" % `Play-3.0-version` % "test-play-30",
  "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.1" % "test-play-30"
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

inConfig(`Test-Play-28`)(Defaults.testSettings ++ instrumentationSettings ++ baseTestSettings ++ Seq(
  sources := joinSources(TestCommon, `Test-Play-28`).value,
  crossScalaVersions := Seq(`scala_2.13_version`),
  testGrouping := singleTestPerJvm(definedTests.value, javaOptions.value),
  unmanagedResourceDirectories ++= (Compile / unmanagedResourceDirectories).value,
  unmanagedResourceDirectories ++= (TestCommon / unmanagedResourceDirectories).value,
  javaOptions ++= Seq(
    "--add-exports=java.base/sun.security.x509=ALL-UNNAMED",
    "--add-opens=java.base/sun.security.ssl=ALL-UNNAMED"
  )
))

inConfig(`Test-Play-29`)(Defaults.testSettings ++ instrumentationSettings ++ baseTestSettings ++ Seq(
  sources := joinSources(TestCommon, `Test-Play-29`).value,
  crossScalaVersions := Seq(`scala_2.13_version`),
  testGrouping := singleTestPerJvm(definedTests.value, javaOptions.value),
  unmanagedResourceDirectories ++= (Compile / unmanagedResourceDirectories).value,
  unmanagedResourceDirectories ++= (TestCommon / unmanagedResourceDirectories).value
))

inConfig(`Test-Play-30`)(Defaults.testSettings ++ instrumentationSettings ++ baseTestSettings ++ Seq(
  sources := joinSources(TestCommon, `Test-Play-30`).value,
  crossScalaVersions := Seq(`scala_2.13_version`),
  testGrouping := singleTestPerJvm(definedTests.value, javaOptions.value),
  unmanagedResourceDirectories ++= (Compile / unmanagedResourceDirectories).value,
  unmanagedResourceDirectories ++= (TestCommon / unmanagedResourceDirectories).value
))

Test / test := Def.taskDyn {
  Def.task {
    (`Test-Play-28` / test).value
    (`Test-Play-29` / test).value
    (`Test-Play-30` / test).value
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
