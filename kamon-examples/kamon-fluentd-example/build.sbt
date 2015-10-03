import sbt._
import sbt.Keys._

name := "kamon-fluentd-example"

version := "1.0"

scalaVersion := "2.11.6"

resolvers += "Kamon repo" at "http://repo.kamon.io"

resolvers += "spray repo" at "http://repo.spray.io"

libraryDependencies ++= {
  val akkaV = "2.3.5"
  val sprayV = "1.3.1"
  val kamonV = "EDIT_HERE"
  Seq(
    "io.spray"            %% "spray-can"            % sprayV,
    "io.spray"            %% "spray-routing"        % sprayV,
    "io.kamon"            %% "kamon-core"           % kamonV,
    "io.kamon"            %% "kamon-system-metrics" % kamonV,
    "io.kamon"            %% "kamon-akka"           % kamonV,
    "io.kamon"            %% "kamon-scala"          % kamonV,
    "io.kamon"            %% "kamon-spray"          % kamonV,
    "io.kamon"            %% "kamon-fluentd"        % kamonV,
    "io.kamon"            %% "kamon-log-reporter"   % kamonV,
    "org.aspectj"         %  "aspectjweaver"        % "1.8.4"
  )
}

