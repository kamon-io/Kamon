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

  val aspectjVersion    = "1.8.9"
  val AkkaVersion       = "2.4.8"
  val KamonVersion      = "0.6.1"

  val aspectJ           = "org.aspectj"          % "aspectjweaver"          % aspectjVersion
  val httpCore          = "com.typesafe.akka"   %% "akka-http-core"         % AkkaVersion
  val httpExperimental  = "com.typesafe.akka"   %% "akka-http-experimental" % AkkaVersion
  val httpTestKit       = "com.typesafe.akka"   %% "akka-http-testkit"      % AkkaVersion
  val kamonCore         = "io.kamon"            %% "kamon-core"             % KamonVersion
  val kamonScala        = "io.kamon"            %% "kamon-scala"            % KamonVersion
  val scalatest         = "org.scalatest"       %% "scalatest"              % "2.2.6"

  def compile   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
  def provided  (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
  def test      (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")
}