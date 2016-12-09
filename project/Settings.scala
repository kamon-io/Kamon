/* =========================================================================================
 * Copyright © 2013-2016 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

import sbt._
import Keys._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import Publish.{settings => publishSettings}
import Release.{settings => releaseSettings}
import scalariform.formatter.preferences._

object Settings {

  lazy val basicSettings = Seq(
    ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) },
    scalaVersion                    := "2.12.1",
    crossScalaVersions              := Seq("2.11.8", "2.12.1"),
    resolvers                       ++= Dependencies.resolutionRepos,
    fork in run                     := true,
    parallelExecution in Global     := false,
    scalacOptions                   := commonScalacOptions,
    javacOptions                    := commonJavaOptions
  ) ++ publishSettings ++ releaseSettings

  lazy val commonJavaOptions = Seq(
    "-Xlint:-options",
    "-source", "1.8", "-target", "1.8")

  lazy val commonScalacOptions = Seq(
    "-encoding",
    "utf8",
    "-g:vars",
    "-feature",
    "-deprecation:false",
    "-unchecked",
    "-deprecation",
    "-language:postfixOps",
    "-language:implicitConversions",
    "-Xlog-reflective-calls",
    "-Ywarn-unused-import",
    "-Ywarn-dead-code",
    "-target:jvm-1.8"
  )

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
