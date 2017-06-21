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

val kamonCore         = "io.kamon"                  %%  "kamon-core"            % "0.6.7"
val kamonTestkit      = "io.kamon"                  %%  "kamon-testkit"         % "0.6.7"
val newrelic          = "com.newrelic.agent.java"   %   "newrelic-agent"        % "3.26.1"
val sprayJson         = "io.spray"                  %%  "spray-json"            % "1.3.3"
val sprayJsonLenses   = "net.virtual-void"          %%  "json-lenses"           % "0.6.2"
val scalaTest         = "org.scalatest"             %%  "scalatest"             % "3.0.3"
val scalaMock         = "org.scalamock"             %%  "scalamock-scalatest-support" % "3.6.0"
val scalajHttp        = "org.scalaj"                %% "scalaj-http"                  % "2.3.0"

lazy val root = (project in file("."))
  .settings(name := "kamon-newrelic")
  .settings(Seq(
    scalaVersion := "2.12.2",
    crossScalaVersions := Seq("2.10.6", "2.11.8", "2.12.2")))
  .settings(
      libraryDependencies ++=
        compileScope(kamonCore, sprayJson, scalajHttp, sprayJsonLenses, newrelic) ++
        providedScope(aspectJ, newrelic) ++
        testScope(scalatest, akkaDependency("testkit").value, scalaTest, scalaMock, kamonTestkit, slf4jApi, slf4jnop))