import sbt._

object Dependencies {

  val resolutionRepos = Seq(
    "spray repo"            at "http://repo.spray.io/",
    "spray nightlies repo"  at "http://nightlies.spray.io"
  )

  val sprayVersion = "1.2-SNAPSHOT"
  val akkaVersion  = "2.2.0"

  val sprayCan        = "io.spray"                  %   "spray-can"             % sprayVersion
  val sprayRouting    = "io.spray"                  %   "spray-routing"         % sprayVersion
  val sprayTestkit    = "io.spray"                  %   "spray-testkit"         % sprayVersion
  val sprayClient     = "io.spray"                  %   "spray-client"          % sprayVersion
  val sprayServlet    = "io.spray"                  %   "spray-servlet"         % sprayVersion
  val sprayJson       = "io.spray"                  %%  "spray-json"            % "1.2.3"
  val scalaReflect    = "org.scala-lang"            %   "scala-reflect"         % "2.10.1"
  val akkaActor       = "com.typesafe.akka"         %%  "akka-actor"            % akkaVersion
  val akkaAgent       = "com.typesafe.akka"         %%  "akka-agent"            % akkaVersion
  val akkaSlf4j       = "com.typesafe.akka"         %%  "akka-slf4j"            % akkaVersion
  val akkaTestKit     = "com.typesafe.akka"         %%  "akka-testkit"          % akkaVersion
  val scalatest       = "org.scalatest"             %   "scalatest_2.10"        % "2.0.M6-SNAP22"
  val logback         = "ch.qos.logback"            %   "logback-classic"       % "1.0.10"
  val aspectJ         = "org.aspectj"               %   "aspectjrt"             % "1.7.2"
  val aspectJWeaver   = "org.aspectj"               %   "aspectjweaver"         % "1.7.2"
  val metrics         = "com.codahale.metrics"      %   "metrics-core"          % "3.0.0"
  val newrelic        = "com.newrelic.agent.java"   %   "newrelic-api"          % "2.19.0"

  def compile   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
  def provided  (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
  def test      (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")
  def runtime   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "runtime")
  def container (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "container")
}
