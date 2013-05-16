import sbt._
import Keys._
import spray.revolver.RevolverPlugin.Revolver

object Settings {
  val VERSION = "0.1-SNAPSHOT"

  lazy val basicSettings = seq(
    version       := VERSION,
    organization  := "com.despegar",
    scalaVersion  := "2.10.1",
    resolvers    ++= Dependencies.resolutionRepos,
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
    )
  )


  import spray.revolver.RevolverPlugin.Revolver._
  lazy val revolverSettings = Revolver.settings ++ seq(
    reJRebelJar := "~/.jrebel/jrebel.jar"
  )


}

