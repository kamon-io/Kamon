import sbt._
import Keys._

object Projects extends Build {
  import AspectJ._
  import Settings._
  import Site._
  import Dependencies._

  lazy val root = Project("root", file("."))
    .aggregate(kamonCore, kamonSpray, kamonNewrelic, kamonPlayground, kamonDashboard, kamonTestkit, kamonPlay, kamonStatsd, site)
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(noPublishing: _*)


  lazy val kamonCore = Project("kamon-core", file("kamon-core"))
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(akkaActor, aspectJ, aspectjWeaver, hdrHistogram, jsr166) ++
        provided(logback) ++
        test(scalatest, akkaTestKit, sprayTestkit, akkaSlf4j, logback))


  lazy val kamonSpray = Project("kamon-spray", file("kamon-spray"))
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(akkaActor, aspectJ, sprayCan, sprayClient, sprayRouting) ++
        test(scalatest, akkaTestKit, sprayTestkit, slf4Api, slf4nop))
    .dependsOn(kamonCore, kamonTestkit)


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
    .dependsOn(kamonSpray, kamonNewrelic, kamonStatsd)


  lazy val kamonDashboard = Project("kamon-dashboard", file("kamon-dashboard"))
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(libraryDependencies ++= compile(akkaActor, akkaSlf4j, sprayRouting, sprayCan, sprayJson))
    .dependsOn(kamonCore)


  lazy val kamonTestkit = Project("kamon-testkit", file("kamon-testkit"))
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(libraryDependencies ++= compile(akkaActor, akkaTestKit) ++ test(slf4Api, slf4nop))
    .dependsOn(kamonCore)

  lazy val kamonPlay = Project("kamon-play", file("kamon-play"))
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(libraryDependencies ++= compile(playTest, aspectJ) ++ test(playTest, akkaTestKit, slf4Api))
    .dependsOn(kamonCore)

  lazy val kamonStatsd = Project("kamon-statsd", file("kamon-statsd"))
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(libraryDependencies ++= compile(akkaActor) ++  test(scalatest, akkaTestKit, slf4Api, slf4nop))
    .dependsOn(kamonCore)

  lazy val site = Project("site", file("site"))
    .settings(basicSettings: _*)
    .settings(siteSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(noPublishing: _*)
    .dependsOn(kamonCore)
    .settings(
      libraryDependencies ++=
        compile(akkaSlf4j, logback) ++
        test(scalatest, akkaTestKit))


  val noPublishing = Seq(publish := (), publishLocal := (), publishArtifact := false)
}
