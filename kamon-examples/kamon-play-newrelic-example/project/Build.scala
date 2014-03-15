import java.io.File
import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "Kamon-Play-NewRelic-Example"
  val appVersion      = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    "kamon" %  "kamon-core" % "0.0.14-SNAPSHOT",
    "kamon" %  "kamon-play" % "0.0.14-SNAPSHOT",
    "kamon" %  "kamon-newrelic" % "0.0.14-SNAPSHOT"
    )


  val main = play.Project(appName, appVersion, appDependencies).settings(
    // For additionally resolving from the conventional ivy local home.
    resolvers += Resolver.file("LocalIvy", file(Path.userHome +
      File.separator + ".ivy2" + File.separator + "local"))(Resolver.ivyStylePatterns))
}
