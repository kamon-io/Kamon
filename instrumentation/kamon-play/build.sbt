import sbt.Tests._
import play.grpc.gen.scaladsl.{PlayScalaServerCodeGenerator, PlayScalaClientCodeGenerator}

val `Play-2.6-version` = "2.6.23"
val `Play-2.7-version` = "2.7.3"
val `Play-2.8-version` = "2.8.2"

/**
  * Test Configurations
  */
lazy val TestCommon = config("test-common") extend(Compile)
lazy val `Test-Play-2.6` = config("test-play-2.6")
lazy val `Test-Play-2.7` = config("test-play-2.7")
lazy val `Test-Play-2.8` = config("test-play-2.8")

configs(
  TestCommon,
  `Test-Play-2.8`,
  `Test-Play-2.7`,
  `Test-Play-2.6`
)

enablePlugins(AkkaGrpcPlugin)

libraryDependencies ++= Seq(
  kanelaAgent % "provided",
  "com.typesafe.play" %%  "play"                  % `Play-2.7-version` % "provided,test-common,test-play-2.7",
  "com.typesafe.play" %%  "play-netty-server"     % `Play-2.7-version` % "provided,test-common,test-play-2.7",
  "com.typesafe.play" %%  "play-akka-http-server" % `Play-2.7-version` % "provided,test-common,test-play-2.7",
  "com.typesafe.play" %%  "play-ws"               % `Play-2.7-version` % "provided,test-common,test-play-2.7",
  "com.typesafe.play" %%  "play-test"             % `Play-2.7-version` % "provided,test-common,test-play-2.7",
  "com.typesafe.play" %%  "play-logback"          % `Play-2.7-version` % "test-common,test-play-2.7",

  scalatest % "test-common,test-play-2.8,test-play-2.7,test-play-2.6",
  "org.scalatestplus.play"  %%  "scalatestplus-play"    % "4.0.3" % "test-play-2.8,test-play-2.7,test-play-2.6"
)

libraryDependencies ++= { if(scalaBinaryVersion.value == "2.13") Seq.empty else Seq(
  "com.typesafe.play" %%  "play"                  % `Play-2.6-version` % "test-play-2.6",
  "com.typesafe.play" %%  "play-netty-server"     % `Play-2.6-version` % "test-play-2.6",
  "com.typesafe.play" %%  "play-akka-http-server" % `Play-2.6-version` % "test-play-2.6",
  "com.typesafe.play" %%  "play-ws"               % `Play-2.6-version` % "test-play-2.6",
  "com.typesafe.play" %%  "play-test"             % `Play-2.6-version` % "test-play-2.6",
  "com.typesafe.play" %%  "play-logback"          % `Play-2.6-version` % "test-play-2.6",
)}

libraryDependencies ++= { if(scalaBinaryVersion.value == "2.11") Seq.empty else Seq(
  "com.lightbend.play" %%  "play-grpc-runtime"    % "0.9.0" % "test-play-2.8",
  "com.lightbend.play" %%  "play-grpc-scalatest"  % "0.9.0" % "test-play-2.8",
  "com.typesafe.play" %%  "play-akka-http2-support" % `Play-2.8-version` % "test-play-2.8",
  "com.typesafe.play" %%  "play"                  % `Play-2.8-version` % "test-play-2.8",
  "com.typesafe.play" %%  "play-netty-server"     % `Play-2.8-version` % "test-play-2.8",
  "com.typesafe.play" %%  "play-akka-http-server" % `Play-2.8-version` % "test-play-2.8",
  "com.typesafe.play" %%  "play-ws"               % `Play-2.8-version` % "test-play-2.8",
  "com.typesafe.play" %%  "play-test"             % `Play-2.8-version` % "test-play-2.8",
  "com.typesafe.play" %%  "play-logback"          % `Play-2.8-version` % "test-play-2.8",
)}

// We are explicitly removing the gRPC-related dependencies because the are manually added
// on the Test configuration for Play 2.8.
PB.additionalDependencies := Seq.empty


/**
  * Test-related settings
  */

lazy val baseTestSettings = Seq(
  fork := true,
  parallelExecution := false,
  javaOptions := (javaOptions in Test).value ++ Seq("-Dkanela.loglevel=DEBUG","-Dkanela.debug-mode=yes"),
  dependencyClasspath += (packageBin in Compile).value,
)

inConfig(TestCommon)(Defaults.testSettings ++ instrumentationSettings ++ baseTestSettings ++ Seq(
  crossScalaVersions := Seq("2.11.12", "2.12.11")
))

inConfig(`Test-Play-2.6`)(Defaults.testSettings ++ instrumentationSettings ++ baseTestSettings ++ Seq(
  sources := joinSources(TestCommon, `Test-Play-2.6`).value,
  crossScalaVersions := Seq("2.11.12", "2.12.11"),
  testGrouping := singleTestPerJvm(definedTests.value, javaOptions.value),
  unmanagedResourceDirectories ++= (unmanagedResourceDirectories in Compile).value,
  unmanagedResourceDirectories ++= (unmanagedResourceDirectories in TestCommon).value,
))

inConfig(`Test-Play-2.7`)(Defaults.testSettings ++ instrumentationSettings ++ baseTestSettings ++ Seq(
  sources := joinSources(TestCommon, `Test-Play-2.7`).value,
  testGrouping := singleTestPerJvm(definedTests.value, javaOptions.value),
  unmanagedResourceDirectories ++= (unmanagedResourceDirectories in Compile).value,
  unmanagedResourceDirectories ++= (unmanagedResourceDirectories in TestCommon).value
))

inConfig(`Test-Play-2.8`)(Defaults.testSettings ++ instrumentationSettings ++ baseTestSettings ++ Seq(
  sources := joinSources(TestCommon, `Test-Play-2.8`).value,
  crossScalaVersions := Seq("2.12.11", "2.13.1"),
  akkaGrpcGeneratedSources := Seq(AkkaGrpc.Server, AkkaGrpc.Client),
  akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Scala),
  akkaGrpcExtraGenerators += PlayScalaServerCodeGenerator,
  akkaGrpcExtraGenerators += PlayScalaClientCodeGenerator,
  testGrouping := singleTestPerJvm(definedTests.value, javaOptions.value),
  unmanagedResourceDirectories ++= (unmanagedResourceDirectories in Compile).value,
  unmanagedResourceDirectories ++= (unmanagedResourceDirectories in TestCommon).value,
))

AkkaGrpcPlugin.configSettings(TestCommon)
AkkaGrpcPlugin.configSettings(`Test-Play-2.8`)

test in Test := Def.taskDyn {
  if(scalaBinaryVersion.value == "2.13")
    Def.task {
      (test in `Test-Play-2.7`).value
      (test in `Test-Play-2.8`).value
    }
  else
    Def.task {
      (test in `Test-Play-2.6`).value
      (test in `Test-Play-2.7`).value
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
        envVars = Map.empty[String, String])
      )
    )
  }
