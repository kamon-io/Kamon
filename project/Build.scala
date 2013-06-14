import sbt._
import Keys._

object Build extends Build {
  import AspectJ._
  import NewRelic._
  import Settings._
  import Dependencies._

  lazy val root = Project("kamon", file("."))
    .settings(basicSettings: _*)
    .settings(revolverSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(newrelicSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(akkaActor, akkaAgent, sprayCan, sprayClient, sprayRouting, sprayServlet, aspectJ, metrics, sprayJson) ++
        test(scalatest, akkaTestKit, sprayTestkit))


}