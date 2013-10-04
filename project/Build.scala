import sbt._
import Keys._

object Build extends Build {
  import AspectJ._
  import NewRelic._
  import Settings._
  import Dependencies._

  lazy val root = Project("root", file("."))
    .aggregate(kamonCore, kamonDashboard)
    .settings(basicSettings: _*)
    .settings(
      publish := (),
      publishLocal := ()
    )

  lazy val kamonCore = Project("kamon-core", file("kamon-core"))
    .settings(basicSettings: _*)
    .settings(revolverSettings: _*)
    .settings(aspectJSettings: _*)
    //.settings(newrelicSettings: _*)

    .settings(
      libraryDependencies ++=
        compile(akkaActor, akkaAgent, aspectJ, aspectJWeaver, metrics, newrelic, sprayJson) ++
        provided(sprayCan, sprayClient, sprayRouting, logback, akkaSlf4j) ++
        test(scalatest, akkaTestKit, sprayTestkit, logback, akkaSlf4j))
    //.dependsOn(kamonDashboard)

  lazy val kamonDashboard = Project("kamon-dashboard", file("kamon-dashboard"))
    .settings(basicSettings: _*)
    .settings(libraryDependencies ++= compile(akkaActor, akkaSlf4j, sprayRouting, sprayCan, sprayJson))
    .dependsOn(kamonCore)
}