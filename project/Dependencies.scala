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
  val aspectjVersion    = "1.8.9"

  val akka23Version       = "2.3.13"
  val akka24Version       = "2.4.14"

  val aspectJ             = "org.aspectj"                 %   "aspectjweaver"         % aspectjVersion

  val kamonCore           = "io.kamon"                    %%  "kamon-core"            % kamonVersion
  val kamonScala          = "io.kamon"                    %%  "kamon-scala"           % kamonVersion
  val kamonTestkit        = "io.kamon"                    %%  "kamon-testkit"         % kamonVersion

  val akkaActor23         = "com.typesafe.akka"           %%  "akka-actor"            % akka23Version
  val akkaSlf4j23         = "com.typesafe.akka"           %%  "akka-slf4j"            % akka23Version
  val akkaTestKit23       = "com.typesafe.akka"           %%  "akka-testkit"          % akka23Version

  val akkaActor24         = "com.typesafe.akka"           %%  "akka-actor"            % akka24Version
  val akkaSlf4j24         = "com.typesafe.akka"           %%  "akka-slf4j"            % akka24Version
  val akkaTestKit24       = "com.typesafe.akka"           %%  "akka-testkit"          % akka24Version

  val scalatest           = "org.scalatest"               %%  "scalatest"             % "2.2.4"
  val logback             = "ch.qos.logback"              %   "logback-classic"       % "1.0.13"

  def compileScope   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
  def providedScope  (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
  def testScope      (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")
  def optionalScope  (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile,optional")
}
