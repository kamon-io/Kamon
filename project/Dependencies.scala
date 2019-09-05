import sbt._

object Versions {
  val kamonCoreVersion = "2.0.0"
  val scalaTestVersion = "3.0.8"
}

object Dependencies {
  import Versions._

  val kamonCore = "io.kamon" %% "kamon-core" % kamonCoreVersion
  val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion
}
