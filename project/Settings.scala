import sbt._
import Keys._
import spray.revolver.RevolverPlugin.Revolver

object Settings {
  val VERSION = "0.0.6"

  lazy val basicSettings = seq(
    version       := VERSION,
    organization  := "kamon",
    scalaVersion  := "2.10.3",
    resolvers    ++= Dependencies.resolutionRepos,
    fork in run   := true,
    scalacOptions := Seq(
      "-encoding",
      "utf8",
      "-g:vars",
      "-feature",
      "-unchecked",
      "-deprecation",
      "-target:jvm-1.6",
      "-language:postfixOps",
      "-language:implicitConversions",
      "-Xlog-reflective-calls"
    ),
    publishTo     := Some("Nexus" at "http://nexus.despegar.it:8080/nexus/content/repositories/releases")
  )


  import spray.revolver.RevolverPlugin.Revolver._
  lazy val revolverSettings = Revolver.settings ++ seq(
    reJRebelJar := "~/.jrebel/jrebel.jar"
  )


}

