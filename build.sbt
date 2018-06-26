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

import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

val kamonCore         = "io.kamon"               %% "kamon-core"          % "1.1.2"
val kamonTestKit      = "io.kamon"               %% "kamon-testkit"       % "1.1.2"

lazy val root = (project in file("."))
    .settings(name := "kamon-graphite")
    .settings(
      libraryDependencies ++=
      compileScope(kamonCore, scalaCompact.value) ++
      testScope(scalatest, slf4jApi, slf4jnop, kamonCore, kamonTestKit),
      ScalariformKeys.preferences := formatSettings(ScalariformKeys.preferences.value))


def scalaCompact = Def.setting {
  scalaBinaryVersion.value match {
    case "2.10" | "2.11" => "org.scala-lang.modules" %% "scala-java8-compat" % "0.5.0"
    case "2.12"          => "org.scala-lang.modules" %% "scala-java8-compat" % "0.8.0"
  }
}

def formatSettings(prefs: IFormattingPreferences) = prefs
    .setPreference(AlignParameters, true)
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 60)
    .setPreference(DoubleIndentConstructorArguments, false)
    .setPreference(DoubleIndentMethodDeclaration, false)
    .setPreference(DanglingCloseParenthesis, Preserve)
    .setPreference(NewlineAtEndOfFile, true)