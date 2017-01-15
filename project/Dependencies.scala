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
    "typesafe repo" at "http://repo.typesafe.com/typesafe/releases/"
  )

  val aspectjVersion    = "1.8.10"
  val AkkaHttpVersion   = "10.0.1"
  val KamonVersion      = "0.6.3"
  val slf4jVersion      = "1.7.7"

  val aspectJ           = "org.aspectj"          % "aspectjweaver"          % aspectjVersion
  val http              = "com.typesafe.akka"   %% "akka-http"              % AkkaHttpVersion
  val httpTestKit       = "com.typesafe.akka"   %% "akka-http-testkit"      % AkkaHttpVersion
  val kamonCore         = "io.kamon"            %% "kamon-core"             % KamonVersion
  val kamonAkka         = "io.kamon"            %% "kamon-akka"             % KamonVersion
  val kamonLogReporter  = "io.kamon"            %% "kamon-log-reporter"     % KamonVersion
  val slf4jApi          = "org.slf4j"            % "slf4j-api"              % slf4jVersion
  val slf4jnop          = "org.slf4j"            % "slf4j-nop"              % slf4jVersion
  val scalatest         = "org.scalatest"       %% "scalatest"              % "3.0.1"

  def compile   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
  def provided  (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
  def test      (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")
}
