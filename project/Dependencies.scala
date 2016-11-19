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
  val play23Version     = "2.3.10"
  val play24Version     = "2.4.8"
  val play25Version     = "2.5.4"

  val aspectJ           = "org.aspectj"               %   "aspectjweaver"         % aspectjVersion

  val kamonCore         = "io.kamon"                  %%  "kamon-core"            % kamonVersion
  val kamonScala        = "io.kamon"                  %%  "kamon-scala"           % kamonVersion

  //play 2.3.x
  val play23            = "com.typesafe.play"         %%  "play"                  % play23Version
  val playWS23          = "com.typesafe.play"         %%  "play-ws"               % play23Version
  val playTest23        = "org.scalatestplus"         %%  "play"                  % "1.2.0"

  //play 2.4.x
  val play24            = "com.typesafe.play"         %%  "play"                  % play24Version
  val playWS24          = "com.typesafe.play"         %%  "play-ws"               % play24Version
  val playTest24        = "org.scalatestplus"         %%  "play"                  % "1.4.0-M2"
  val typesafeConfig    = "com.typesafe"              %   "config"                % "1.2.1"

  //play 2.5.x
  val play25            = "com.typesafe.play"         %%  "play"                  % play25Version
  val playWS25          = "com.typesafe.play"         %%  "play-ws"               % play25Version
  val playTest25        = "org.scalatestplus.play"    %%  "scalatestplus-play"    % "1.5.0"

  def compileScope   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
  def providedScope  (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
  def testScope      (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")
}
