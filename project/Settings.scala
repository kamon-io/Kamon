import sbt._
import Keys._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import Publish.{settings => publishSettings}
import Release.{settings => releaseSettings}
import scalariform.formatter.preferences._
import net.virtualvoid.sbt.graph.Plugin.graphSettings

object Settings {

  val ScalaVersion = "2.10.4"

  lazy val basicSettings = Seq(
    scalaVersion  := ScalaVersion,
    resolvers    ++= Dependencies.resolutionRepos,
    fork in run   := true,
    javacOptions  := Seq(
      "-Xlint:-options",
      "-source", "1.6", "-target", "1.6"
    ),
    scalacOptions := Seq(
      "-encoding",
      "utf8",
      "-g:vars",
      "-feature",
      "-unchecked",
      "-optimise",
      "-deprecation",
      "-target:jvm-1.6",
      "-language:postfixOps",
      "-language:implicitConversions",
      "-Xlog-reflective-calls"
    )) ++ publishSettings ++ releaseSettings ++ graphSettings

  lazy val formatSettings = SbtScalariform.scalariformSettings ++ Seq(
    ScalariformKeys.preferences in Compile := formattingPreferences,
    ScalariformKeys.preferences in Test    := formattingPreferences
  )

  def formattingPreferences =
    FormattingPreferences()
      .setPreference(RewriteArrowSymbols, true)
      .setPreference(AlignParameters, false)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(DoubleIndentClassDeclaration, true)
}