import sbt._
import Keys._
import spray.revolver.RevolverPlugin.Revolver

object Settings {
  val VERSION = "0.2-SNAPSHOT"

  lazy val basicSettings = seq(
    version       := VERSION,
    organization  := "kamon",
    scalaVersion  := "2.10.2",
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
    publishTo     := Some("Nexus" at "http://nexus.despegar.it:8080/nexus/content/repositories/snapshots")
  )


  import spray.revolver.RevolverPlugin.Revolver._
  lazy val revolverSettings = Revolver.settings ++ seq(
    reJRebelJar := "~/.jrebel/jrebel.jar"
  )


}

