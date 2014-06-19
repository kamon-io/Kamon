import sbt._

object Dependencies {

  val resolutionRepos = Seq(
       "spray repo" at "http://repo.spray.io/",
    "typesafe repo" at "http://repo.typesafe.com/typesafe/releases/"
  )

  val sprayVersion    = "1.2.0"
  val akkaVersion     = "2.2.3"
  val playVersion     = "2.2.2"
  val aspectjVersion  = "1.7.4"
  val slf4jVersion    = "1.7.6"

  val sprayJson       = "io.spray"                  %%  "spray-json"            % "1.2.5"
  val sprayJsonLenses = "net.virtual-void"          %%  "json-lenses"           % "0.5.3"
  val scalatest       = "org.scalatest"             %   "scalatest_2.10"        % "2.0"
  val logback         = "ch.qos.logback"            %   "logback-classic"       % "1.0.13"
  val aspectJ         = "org.aspectj"               %   "aspectjrt"             % aspectjVersion
  val aspectjWeaver   = "org.aspectj"               %   "aspectjweaver"         % aspectjVersion
  val newrelic        = "com.newrelic.agent.java"   %   "newrelic-api"          % "3.1.0"
  val snakeYaml       = "org.yaml"                  %   "snakeyaml"             % "1.13"
  val hdrHistogram    = "org.hdrhistogram"          %   "HdrHistogram"          % "1.2.1"
  val sprayCan        = "io.spray"                  %   "spray-can"             % sprayVersion
  val sprayRouting    = "io.spray"                  %   "spray-routing"         % sprayVersion
  val sprayTestkit    = "io.spray"                  %   "spray-testkit"         % sprayVersion
  val sprayClient     = "io.spray"                  %   "spray-client"          % sprayVersion
  val akkaActor       = "com.typesafe.akka"         %%  "akka-actor"            % akkaVersion
  val akkaSlf4j       = "com.typesafe.akka"         %%  "akka-slf4j"            % akkaVersion
  val akkaTestKit     = "com.typesafe.akka"         %%  "akka-testkit"          % akkaVersion
  val playTest        = "com.typesafe.play"         %%  "play-test"             % playVersion
  val slf4Api         = "org.slf4j"                 %   "slf4j-api"             % slf4jVersion
  val slf4nop         = "org.slf4j"                 %   "slf4j-nop"             % slf4jVersion
  val jsr166          = "io.gatling"                %   "jsr166e"               % "1.0"


  def compile   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
  def provided  (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
  def test      (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")
  def runtime   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "runtime")
  def container (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "container")
}
