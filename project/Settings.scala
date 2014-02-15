import sbt._
import Keys._
import spray.revolver.RevolverPlugin.Revolver
import sbtrelease.ReleasePlugin._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

object Settings {

  lazy val basicSettings = seq(
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
    publishTo <<= version { (v: String) =>

      if (v.trim.endsWith("SNAPSHOT"))
        Some(Resolver.sftp("Kamon Snapshots Repository", "snapshots.kamon.io", "/var/local/snapshots-repo"))
      else
        Some(Resolver.sftp("Kamon Repository", "repo.kamon.io", "/var/local/releases-repo"))
    }
    ) ++ releaseSettings

  pomExtra := {
      <url>http://kamon.io</url>
      <licenses>
        <license>
          <name>Apache 2</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        </license>
      </licenses>
      <scm>
        <url>git://github.com/kamon-io/Kamon.git</url>
        <connection>scm:git:git@github.com:kamon-io/Kamon.git</connection>
      </scm>
      <developers>
        <developer><id>ivantopo</id><name>Ivan Topolnjak</name><url>https://twitter.com/ivantopo</url></developer>
        <developer><id>dpsoft</id><name>Diego Parra</name><url>https://twitter.com/diegolparra</url></developer>
      </developers>
  }

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

