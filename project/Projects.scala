/*
 * =========================================================================================
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
import sbt.Keys._


object Projects extends Build {
  import Dependencies._
  import Settings._
  import AspectJ._

  lazy val kamonAkkaHttpRoot = Project("kamon-akka-http-root",file("."))
    .aggregate(kamonAkkaHttp, kamonAkkaHttpPlayground)
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(noPublishing: _*)

  lazy val kamonAkkaHttp = Project("kamon-akka-http",file("kamon-akka-http"))
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(libraryDependencies ++=
      compile(http, kamonCore, kamonAkka) ++
      test(httpTestKit, scalatest, slf4jApi, slf4jnop) ++
      provided(aspectJ))

  lazy val kamonAkkaHttpPlayground = Project("kamon-akka-http-playground",file("kamon-akka-http-playground"))
    .dependsOn(kamonAkkaHttp)
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(aspectjSettingsInRun: _*)
    .settings(libraryDependencies ++=
      compile(http, kamonLogReporter) ++
      test(httpTestKit, scalatest, slf4jApi, slf4jnop) ++
      provided(aspectJ))
    .settings(noPublishing: _*)
    .settings(settingsForPlayground: _*)

  val noPublishing = Seq(publish := (), publishLocal := (), publishArtifact := false)
}
