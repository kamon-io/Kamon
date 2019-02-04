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

import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import scalariform.formatter.preferences._


val kamonCore             = "io.kamon"               %% "kamon-core"          % "1.1.2"
val kamonTestKit          = "io.kamon"               %% "kamon-testkit"       % "1.1.2"
val asyncHttpClient       = "com.squareup.okhttp3"    % "okhttp"              % "3.10.0"
val asyncHttpClientMock   = "com.squareup.okhttp3"    % "mockwebserver"       % "3.10.0"

lazy val root = (project in file("."))
  .settings(name := "kamon-datadog")
  .settings(
    libraryDependencies ++=
      compileScope(kamonCore, asyncHttpClient, scalaCompact.value, playJsonVersion.value) ++
        testScope(scalatest, slf4jApi, slf4jnop, kamonCore, kamonTestKit, asyncHttpClientMock),
    ScalariformKeys.preferences := formatSettings(ScalariformKeys.preferences.value))


def playJsonVersion = Def.setting {
  scalaBinaryVersion.value match {
    case "2.10"          => "com.typesafe.play"      %% "play-json"          % "2.4.11"
    case "2.12" | "2.11" => "com.typesafe.play"      %% "play-json"          % "2.6.9"
  }
}


def scalaCompact = Def.setting {
  scalaBinaryVersion.value match {
    case "2.10" | "2.11" => "org.scala-lang.modules" %% "scala-java8-compat" % "0.5.0"
    case "2.12"          => "org.scala-lang.modules" %% "scala-java8-compat" % "0.8.0"
  }
}

/* Changing Kamon configuration in real-time seems to turn tests unstable */ 
parallelExecution in Test := false

def formatSettings(prefs: IFormattingPreferences) = prefs
  .setPreference(AlignParameters, true)
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 60)
  .setPreference(DoubleIndentConstructorArguments, false)
  .setPreference(DoubleIndentMethodDeclaration, false)
  .setPreference(DanglingCloseParenthesis, Preserve)
  .setPreference(NewlineAtEndOfFile, true)
