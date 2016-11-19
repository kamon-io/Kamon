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


import AspectJ._
import Settings._
import Dependencies._

lazy val kamonPlay = Project("kamon-play", file("."))
  .enablePlugins(CrossPerProjectPlugin)
  .settings(noPublishing: _*)

lazy val kamonPlay23 = Project("kamon-play-23", file("kamon-play-2.3.x"))
  .settings(basicSettings: _*)
  .settings(formatSettings: _*)
  .settings(aspectJSettings: _*)
  .settings(
    libraryDependencies ++=
      compileScope(play23, playWS23, kamonCore, kamonScala) ++
      providedScope(aspectJ) ++
      testScope(playTest23))

lazy val kamonPlay24 = Project("kamon-play-24", file("kamon-play-2.4.x"))
  .settings(basicSettings: _*)
  .settings(formatSettings: _*)
  .settings(aspectJSettings: _*)
  .settings(
    libraryDependencies ++=
      compileScope(play24, playWS24, kamonCore, kamonScala) ++
      providedScope(aspectJ, typesafeConfig) ++
      testScope(playTest24))

lazy val kamonPlay25 = Project("kamon-play-25", file("kamon-play-2.5.x"))
  .settings(basicSettings: _*)
  .settings(formatSettings: _*)
  .settings(aspectJSettings: _*)
  .settings(
    crossScalaVersions := Seq(SVersion),
    libraryDependencies ++=
      compileScope(play25, playWS25, kamonCore, kamonScala) ++
      providedScope(aspectJ, typesafeConfig) ++
      testScope(playTest25))

def noPublishing = Seq(publish := (), publishLocal := (), publishArtifact := false)
