import sbt.Tests._

val `Play-3.0-version` = "3.0.7"

libraryDependencies ++= Seq(
  kanelaAgent % "provided",
  "org.playframework" %% "play" % `Play-3.0-version` % "provided,test",
  "org.playframework" %% "play-netty-server" % `Play-3.0-version` % "provided,test",
  "org.playframework" %% "play-pekko-http-server" % `Play-3.0-version` % "provided,test",
  "org.playframework" %% "play-ws" % `Play-3.0-version` % "provided,test",
  "org.playframework" %% "play-pekko-http2-support" % `Play-3.0-version` % "test",
  "org.playframework" %% "play-test" % `Play-3.0-version` % "test",
  "org.playframework" %% "play-logback" % `Play-3.0-version` % "test",
  "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.1" % "test",
  scalatest % "test"
)

Test / parallelExecution := false
Test / javaOptions ++= Seq("-Dkanela.loglevel=DEBUG", "-Dkanela.debug-mode=yes")
// Each spec sets the config.file system property and starts its own Play server, so each one runs in its own JVM
Test / testGrouping := singleTestPerJvm((Test / definedTests).value, (Test / javaOptions).value)

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
