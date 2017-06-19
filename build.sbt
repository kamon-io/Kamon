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

val kamonCore         = "io.kamon"               %% "kamon-core"          % "1.0.0-RC1-7aeeedad6f6684f8aae018fbf433557b2a587172"
val asyncHttpClient   = "org.asynchttpclient"     % "async-http-client"   % "2.0.25"

lazy val root = (project in file("."))
  .settings(name := "kamon-datadog")
  .settings(
      libraryDependencies ++=
        compileScope(kamonCore, asyncHttpClient, scalaCompact.value) ++
        testScope(scalatest, slf4jApi, slf4jnop))



def scalaCompact = Def.setting {
  scalaBinaryVersion.value match {
    case "2.10" | "2.11" => "org.scala-lang.modules" %% "scala-java8-compat"  % "0.5.0"
    case "2.12"          => "org.scala-lang.modules" %% "scala-java8-compat"  % "0.8.0"
   }
 }
