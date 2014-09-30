import java.io.File
import sbt._
import Keys._
import play.Play.autoImport._
import sbt.Keys._
import sbt._
import com.typesafe.sbt.web.SbtWeb


object ApplicationBuild extends Build {

  val appName         = "Kamon-Play-Example"
  val appVersion      = "1.0-SNAPSHOT"

  val resolutionRepos = Seq(
      "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
      "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases",
      "Kamon Repository Snapshots" at "http://snapshots.kamon.io"
    )

  val defaultSettings = Seq(
      scalaVersion := "2.11.0",
      resolvers ++= resolutionRepos,
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
      ))

  val dependencies = Seq(
    "io.kamon"    %% "kamon-core"           % "0.3.4",
    "io.kamon"    %% "kamon-play"           % "0.3.4",
    "io.kamon"    %% "kamon-statsd"         % "0.3.4",
    "io.kamon"    %% "kamon-log-reporter"   % "0.3.4",
    "io.kamon"    %% "kamon-system-metrics" % "0.3.4",
    "org.aspectj" % "aspectjweaver"         % "1.8.1"
    )

  val main = Project(appName, file(".")).enablePlugins(play.PlayScala, SbtWeb)
                                        .settings(libraryDependencies ++= dependencies)
                                        .settings(defaultSettings: _*)
}
