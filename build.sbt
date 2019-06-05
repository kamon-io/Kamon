/* =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
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

val kamonCore     = "io.kamon" %%  "kamon-core"     % "2.0.0-M5"
val kamonTestkit  = "io.kamon" %%  "kamon-testkit"  % "2.0.0-M5"
val kanela        = "io.kamon" %   "kanela-agent"   % "1.0.0-M3"
val kamonCommon   = "io.kamon" %%  "kamon-instrumentation-common"  % "2.0.0-M2"

val latestLogbackClassic  = "ch.qos.logback"  %   "logback-classic" % "1.2.3"

resolvers += Resolver.bintrayRepo("kamon-io", "snapshots")
resolvers += Resolver.mavenLocal

lazy val root = (project in file("."))
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    name := "kamon-logback",
    scalaVersion := "2.12.6",
    libraryDependencies ++=
      compileScope(kamonCore, kamonCommon) ++
      providedScope(kanela, latestLogbackClassic) ++
      testScope(kamonTestkit, scalatest))
