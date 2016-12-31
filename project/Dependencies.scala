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
import sbt.Keys.scalaBinaryVersion

object Dependencies {

  val resolutionRepos = Seq(
    "typesafe repo" at "http://repo.typesafe.com/typesafe/releases/",
    "Kamon Repository Snapshots" at "http://snapshots.kamon.io"
  )

  val kamonVersion      = "0.6.5"
  val aspectjVersion    = "1.8.10"
  val slf4jVersion      = "1.7.7"

  val akkaVersion210    = "2.3.14"
  val akkaVersion212    = "2.4.16"

  val kamonCore         = "io.kamon"                  %%  "kamon-core"            % kamonVersion
  val kamonTestkit      = "io.kamon"                  %%  "kamon-testkit"         % kamonVersion

  val sigarLoader       = "io.kamon"                  %   "sigar-loader"          % "1.6.5-rev002"

  val scalatest         = "org.scalatest"             %%  "scalatest"             % "3.0.1"
  val logback           = "ch.qos.logback"            %   "logback-classic"       % "1.0.13"

  val slf4jJul          = "org.slf4j"                 %   "jul-to-slf4j"          % slf4jVersion

  def akkaDependency(moduleName: String) = Def.setting {
    scalaBinaryVersion.value match {
      case "2.10"           => "com.typesafe.akka" %%  s"akka-$moduleName" % akkaVersion210
      case "2.11" | "2.12"  => "com.typesafe.akka" %%  s"akka-$moduleName" % akkaVersion212
    }
  }

  def compileScope   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
  def testScope      (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")
}
