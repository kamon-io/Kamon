import sbt._

object Dependencies {

  val resolutionRepos = Seq(
    "spray repo"            at "http://repo.spray.io/",
    "spray nightlies repo"  at "http://nightlies.spray.io"
  )

  val sprayCan        = "io.spray"                  %   "spray-can"             % "1.1-M8"
  val sprayRouting    = "io.spray"                  %   "spray-routing"         % "1.1-M8"
  val sprayTestkit    = "io.spray"                  %   "spray-testkit"         % "1.1-M8"
  val sprayClient     = "io.spray"                  %   "spray-client"          % "1.1-M8"
  val sprayServlet    = "io.spray"                  %   "spray-servlet"         % "1.1-M8"
  val sprayJson       = "io.spray"                  %%  "spray-json"            % "1.2.3"
  val scalaReflect    = "org.scala-lang"            %   "scala-reflect"         % "2.10.1"
  val akkaActor       = "com.typesafe.akka"         %%  "akka-actor"            % "2.2.0"
  val akkaAgent       = "com.typesafe.akka"         %%  "akka-agent"            % "2.2.0"
  val akkaSlf4j       = "com.typesafe.akka"         %%  "akka-slf4j"            % "2.2.0"
  val akkaTestKit     = "com.typesafe.akka"         %%  "akka-testkit"          % "2.2.0"
  val scalatest       = "org.scalatest"             %   "scalatest_2.10"        % "2.0.M6-SNAP22"
  val logback         = "ch.qos.logback"            %   "logback-classic"       % "1.0.10"
  val aspectJ         = "org.aspectj"               %   "aspectjrt"             % "1.7.2"
  val metrics         = "com.codahale.metrics"      %   "metrics-core"          % "3.0.0"
  val newrelic        = "com.newrelic.agent.java"   %   "newrelic-api"          % "2.19.0"

  def compile   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
  def provided  (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
  def test      (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")
  def runtime   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "runtime")
  def container (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "container")
}
