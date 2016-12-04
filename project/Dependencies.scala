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

object Dependencies {

  val resolutionRepos = Seq(
    "typesafe repo" at "http://repo.typesafe.com/typesafe/releases/",
    "Kamon Repository Snapshots" at "http://snapshots.kamon.io"
  )

  val kamonVersion      = "0.6.3"
  val akkaVersion       = "2.3.14"
  val sprayVersion      = "1.3.3"
  val slf4jVersion      = "1.7.7"
  val aspectjVersion    = "1.8.9"

  val kamonCore         = "io.kamon"                  %%  "kamon-core"            % kamonVersion
  val kamonAkka         = "io.kamon"                  %%  "kamon-akka"            % kamonVersion
  val kamonTestkit      = "io.kamon"                  %%  "kamon-testkit"         % kamonVersion

  val aspectJ           = "org.aspectj"               %   "aspectjweaver"         % aspectjVersion

  val akkaSlf4j         = "com.typesafe.akka"         %%  "akka-slf4j"            % akkaVersion
  val akkaTestKit       = "com.typesafe.akka"         %%  "akka-testkit"          % akkaVersion

  val sprayCan          = "io.spray"                  %%  "spray-can"             % sprayVersion
  val sprayRouting      = "io.spray"                  %%  "spray-routing"         % sprayVersion
  val sprayTestkit      = "io.spray"                  %%  "spray-testkit"         % sprayVersion
  val sprayClient       = "io.spray"                  %%  "spray-client"          % sprayVersion
  val sprayJson         = "io.spray"                  %%  "spray-json"            % "1.3.1"
  val sprayJsonLenses   = "net.virtual-void"          %%  "json-lenses"           % "0.6.0"

  val newrelic          = "com.newrelic.agent.java"   %   "newrelic-agent"        % "3.26.1"

  val slf4jApi          = "org.slf4j"                 %   "slf4j-api"             % slf4jVersion
  val slf4jnop          = "org.slf4j"                 %   "slf4j-nop"             % slf4jVersion

  val scalatest         = "org.scalatest"             %%  "scalatest"             % "2.2.4"

  def compileScope   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
  def testScope      (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")
  def providedScope  (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
}
