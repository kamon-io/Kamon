name := "kamon-play-example-2.5.x"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.8"

val kamonVersion = "0.6.6"

libraryDependencies ++= Seq(
  "io.kamon"   %% "kamon-play-2.5"       % kamonVersion,
  "io.kamon"   %% "kamon-system-metrics" % kamonVersion,
  "io.kamon"   %% "kamon-statsd"         % kamonVersion,
  "io.kamon"   %% "kamon-log-reporter"   % kamonVersion,
  "org.aspectj" % "aspectjweaver"        % "1.8.9"
)

enablePlugins(PlayScala)