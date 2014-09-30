import sbt._

object Dependencies {

  val resolutionRepos = Seq(
    "spray repo" at "http://repo.spray.io/",
    "typesafe repo" at "http://repo.typesafe.com/typesafe/releases/"
  )

  val sprayVersion    = "1.3.1"
  val akkaVersion     = "2.3.3"
  val aspectjVersion  = "1.8.2"
  val slf4jVersion    = "1.7.7"
  val playVersion     = "2.3.0"

  val sprayJson       = "io.spray"                  %%  "spray-json"            % "1.2.6"
  val sprayJsonLenses = "net.virtual-void"          %%  "json-lenses"           % "0.5.4"
  val scalatest       = "org.scalatest"             %   "scalatest_2.11"        % "2.1.7"
  val logback         = "ch.qos.logback"            %   "logback-classic"       % "1.0.13"
  val aspectJ         = "org.aspectj"               %   "aspectjrt"             % aspectjVersion
  val aspectjWeaver   = "org.aspectj"               %   "aspectjweaver"         % aspectjVersion
  val newrelic        = "com.newrelic.agent.java"   %   "newrelic-api"          % "3.1.0"
  val snakeYaml       = "org.yaml"                  %   "snakeyaml"             % "1.13"
  val hdrHistogram    = "org.hdrhistogram"          %   "HdrHistogram"          % "1.0.8"
  val sprayCan        = "io.spray"                  %%  "spray-can"             % sprayVersion
  val sprayRouting    = "io.spray"                  %%  "spray-routing"         % sprayVersion
  val sprayTestkit    = "io.spray"                  %%  "spray-testkit"         % sprayVersion
  val sprayClient     = "io.spray"                  %%  "spray-client"          % sprayVersion
  val akkaActor       = "com.typesafe.akka"         %%  "akka-actor"            % akkaVersion
  val akkaSlf4j       = "com.typesafe.akka"         %%  "akka-slf4j"            % akkaVersion
  val akkaTestKit     = "com.typesafe.akka"         %%  "akka-testkit"          % akkaVersion
  val akkaRemote      = "com.typesafe.akka"         %%  "akka-remote"           % akkaVersion
  val akkaCluster     = "com.typesafe.akka"         %%  "akka-cluster"          % akkaVersion
  val play            = "com.typesafe.play"         %%  "play"                  % playVersion
  val playWS          = "com.typesafe.play"         %%  "play-ws"               % playVersion
  val playTest        = "org.scalatestplus"         %%  "play"                  % "1.1.0"
  val slf4Api         = "org.slf4j"                 %   "slf4j-api"             % slf4jVersion
  val slf4nop         = "org.slf4j"                 %   "slf4j-nop"             % slf4jVersion
  val scalaCompiler   = "org.scala-lang"            %   "scala-compiler"        % Settings.ScalaVersion
  val sigar           = "org.fusesource"            %   "sigar"                 % "1.6.4"
  val scalazConcurrent = "org.scalaz"               %%  "scalaz-concurrent"     % "7.1.0"

  def compile   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
  def provided  (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
  def test      (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")
  def runtime   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "runtime")
  def container (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "container")
  def optional  (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile,optional")
}
