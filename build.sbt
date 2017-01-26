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


val kamonCore = "io.kamon" %% "kamon-core" % "0.6.5"
val kamonScala = "io.kamon" %% "kamon-scala" % "0.6.5"

lazy val `kamon-akka` = (project in file("."))
    .settings(noPublishing: _*)
    .aggregate(kamonAkka23, kamonAkka24)


lazy val kamonAkka23 = Project("kamon-akka-23", file("kamon-akka-2.3.x"))
  .settings(Seq(
    bintrayPackage := "kamon-akka",
    moduleName := "kamon-akka-2.3",
    scalaVersion := "2.11.8",
    crossScalaVersions := Seq("2.10.6", "2.11.8")))
  .settings(aspectJSettings: _*)
  .settings(
    libraryDependencies ++=
      compileScope(akkaDependency("actor").value, kamonCore, kamonScala) ++
      providedScope(aspectJ) ++
      optionalScope(logbackClassic) ++
      testScope(scalatest, akkaDependency("testkit").value, akkaDependency("slf4j").value, logbackClassic))

lazy val kamonAkka24 = Project("kamon-akka-24", file("kamon-akka-2.4.x"))
  .settings(Seq(
    bintrayPackage := "kamon-akka",
    moduleName := "kamon-akka-2.4",
    scalaVersion := "2.12.1",
    crossScalaVersions := Seq("2.11.8", "2.12.1")))
  .settings(aspectJSettings: _*)
  .settings(
    libraryDependencies ++=
      compileScope(akkaDependency("actor").value, kamonCore, kamonScala) ++
      providedScope(aspectJ) ++
      optionalScope(logbackClassic) ++
      testScope(scalatest, akkaDependency("testkit").value, akkaDependency("slf4j").value, logbackClassic))

enableProperCrossScalaVersionTasks
