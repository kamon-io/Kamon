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
scalaVersion := "2.11.8"
isSnapshot := true
crossScalaVersions := Seq("2.10.6", "2.11.8", "2.12.2")

resolvers += Resolver.bintrayRepo("kamon-io", "snapshots")
val kamonCore    = "io.kamon" %% "kamon-core"    % "1.0.0-RC5"
val kamonTestkit = "io.kamon" %% "kamon-testkit" % "1.0.0-RC5"
val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"

lazy val root = (project in file("."))
  .settings(name := "kamon-executors")
  .settings(aspectJSettings: _*)
  .settings(
      libraryDependencies ++=
      compileScope(kamonCore, logback) ++
      testScope(scalatest, logbackClassic, kamonTestkit) ++
      providedScope(aspectJ)
  )

fork := true
