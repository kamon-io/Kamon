import sbt._
import Keys._

object Build extends Build {
  import AspectJ._
  import Settings._
  import Dependencies._

  lazy val root = Project("kamon", file("."))
    .settings(basicSettings: _*)
    .settings(revolverSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(akkaActor, sprayCan, sprayClient, sprayRouting, sprayServlet, aspectJ, metrics, newrelic, metricsScala, sprayJson, guava) ++
        test(scalatest, sprayTestkit))


}