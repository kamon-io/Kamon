import sbt._
import Keys._
import spray.revolver.RevolverPlugin.Revolver
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

object Settings {
  val VERSION = "0.0.11"

  lazy val basicSettings = seq(
    version       := VERSION,
    organization  := "kamon",
    scalaVersion  := "2.10.3",
    resolvers    ++= Dependencies.resolutionRepos,
    fork in run   := true,
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
    ),
    publishTo     := Some("Nexus" at "http://nexus.despegar.it:8080/nexus/content/repositories/releases")
  )


  import spray.revolver.RevolverPlugin.Revolver._
  lazy val revolverSettings = Revolver.settings ++ seq(reJRebelJar := "~/.jrebel/jrebel.jar")
  
  lazy val formatSettings = SbtScalariform.scalariformSettings ++ Seq(
    ScalariformKeys.preferences in Compile := formattingPreferences,
    ScalariformKeys.preferences in Test    := formattingPreferences
  )

  import scalariform.formatter.preferences._
  def formattingPreferences =
    FormattingPreferences()
      .setPreference(RewriteArrowSymbols, true)
      .setPreference(AlignParameters, true)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(DoubleIndentClassDeclaration, true)
}

