import sbt._
import Keys._

object Projects extends Build {
  import AspectJ._
  import Settings._
  import Dependencies._

  lazy val root = Project("root", file("."))
    .aggregate(kamonCore, kamonSpray, kamonNewrelic, kamonPlayground, kamonDashboard, kamonTestkit, kamonPlay, kamonStatsD,
      kamonDatadog, kamonSystemMetrics, kamonLogReporter)
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(noPublishing: _*)


  lazy val kamonCore = Project("kamon-core", file("kamon-core"))
    .dependsOn(kamonMacros % "compile-internal, test-internal")
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(
      mappings in (Compile, packageBin) ++= mappings.in(kamonMacros, Compile, packageBin).value,
      mappings in (Compile, packageSrc) ++= mappings.in(kamonMacros, Compile, packageSrc).value,
      libraryDependencies ++=
        compile(akkaActor, aspectJ, aspectjWeaver, hdrHistogram, jsr166) ++
        provided(logback) ++
        test(scalatest, akkaTestKit, sprayTestkit, akkaSlf4j, logback))


  lazy val kamonSpray = Project("kamon-spray", file("kamon-spray"))
    .dependsOn(kamonMacros % "compile-internal, test-internal")
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(
      mappings in (Compile, packageBin) ++= mappings.in(kamonMacros, Compile, packageBin).value,
      mappings in (Compile, packageSrc) ++= mappings.in(kamonMacros, Compile, packageSrc).value,
      libraryDependencies ++=
        compile(akkaActor, aspectJ, sprayCan, sprayClient, sprayRouting) ++
        test(scalatest, akkaTestKit, sprayTestkit, slf4Api, slf4nop))
    .dependsOn(kamonCore)
    .dependsOn(kamonTestkit % "test")


  lazy val kamonNewrelic = Project("kamon-newrelic", file("kamon-newrelic"))
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(aspectJ, sprayCan, sprayClient, sprayRouting, sprayJson, sprayJsonLenses, newrelic, snakeYaml) ++
        test(scalatest, akkaTestKit, sprayTestkit, slf4Api, slf4nop))
    .dependsOn(kamonCore)


  lazy val kamonPlayground = Project("kamon-playground", file("kamon-playground"))
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(noPublishing: _*)
    .settings(
      libraryDependencies ++=
        compile(akkaActor, akkaSlf4j, sprayCan, sprayClient, sprayRouting, logback))
    .dependsOn(kamonSpray, kamonNewrelic, kamonStatsD, kamonDatadog, kamonLogReporter, kamonSystemMetrics)


  lazy val kamonDashboard = Project("kamon-dashboard", file("kamon-dashboard"))
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(libraryDependencies ++= compile(akkaActor, akkaSlf4j, sprayRouting, sprayCan, sprayJson))
    .dependsOn(kamonCore)


  lazy val kamonTestkit = Project("kamon-testkit", file("kamon-testkit"))
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(
      libraryDependencies ++= compile(akkaActor, akkaTestKit) ++ test(slf4Api, slf4nop))
    .dependsOn(kamonCore)

  lazy val kamonPlay = Project("kamon-play", file("kamon-play"))
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(libraryDependencies ++= compile(play, playWS, aspectJ) ++ test(playTest, akkaTestKit, slf4Api))
    .dependsOn(kamonCore)

  lazy val kamonStatsD = Project("kamon-statsd", file("kamon-statsd"))
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(libraryDependencies ++= compile(akkaActor) ++  test(scalatest, akkaTestKit, slf4Api, slf4nop))
    .dependsOn(kamonCore)
    .dependsOn(kamonSystemMetrics % "provided")
 
  lazy val kamonDatadog = Project("kamon-datadog", file("kamon-datadog"))
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(libraryDependencies ++= compile(akkaActor) ++ test(scalatest, akkaTestKit, slf4Api, slf4nop))
    .dependsOn(kamonCore)
    .dependsOn(kamonSystemMetrics % "provided")

  lazy val kamonLogReporter = Project("kamon-log-reporter", file("kamon-log-reporter"))
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(libraryDependencies ++= compile(akkaActor) ++  test(scalatest, akkaTestKit, slf4Api, slf4nop))
    .dependsOn(kamonCore)
    .dependsOn(kamonSystemMetrics % "provided")

  lazy val kamonMacros = Project("kamon-macros", file("kamon-macros"))
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(noPublishing: _*)
    .settings(libraryDependencies ++= compile(scalaCompiler))

  lazy val kamonSystemMetrics = Project("kamon-system-metrics", file("kamon-system-metrics"))
      .settings(basicSettings: _*)
      .settings(formatSettings: _*)
      .settings(libraryDependencies ++= compile(sigar) ++ test(scalatest, akkaTestKit, slf4Api, slf4nop))
      .settings(fork in Test :=  true)
      .dependsOn(kamonCore)

  val noPublishing = Seq(publish := (), publishLocal := (), publishArtifact := false)
}
