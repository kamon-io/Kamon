/* =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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
    "spray repo" at "http://repo.spray.io/",
    "typesafe repo" at "http://repo.typesafe.com/typesafe/releases/"
  )

  val sprayVersion      = "1.3.3"
  val akkaVersion       = "2.3.14"
  val aspectjVersion    = "1.8.7"
  val slf4jVersion      = "1.7.7"
  val play23Version     = "2.3.10"
  val play24Version     = "2.4.4"

  val sprayJson         = "io.spray"                  %%  "spray-json"            % "1.3.1"
  val sprayJsonLenses   = "net.virtual-void"          %%  "json-lenses"           % "0.6.0"
  val scalatest         = "org.scalatest"             %%  "scalatest"             % "2.2.4"
  val logback           = "ch.qos.logback"            %   "logback-classic"       % "1.0.13"
  val aspectJ           = "org.aspectj"               %   "aspectjweaver"         % aspectjVersion
  val newrelic          = "com.newrelic.agent.java"   %   "newrelic-agent"        % "3.11.0"
  val hdrHistogram      = "org.hdrhistogram"          %   "HdrHistogram"          % "2.1.7"
  val sprayCan          = "io.spray"                  %%  "spray-can"             % sprayVersion
  val sprayRouting      = "io.spray"                  %%  "spray-routing"         % sprayVersion
  val sprayTestkit      = "io.spray"                  %%  "spray-testkit"         % sprayVersion
  val sprayClient       = "io.spray"                  %%  "spray-client"          % sprayVersion
  val akkaActor         = "com.typesafe.akka"         %%  "akka-actor"            % akkaVersion
  val akkaSlf4j         = "com.typesafe.akka"         %%  "akka-slf4j"            % akkaVersion
  val akkaTestKit       = "com.typesafe.akka"         %%  "akka-testkit"          % akkaVersion
  val akkaRemote        = "com.typesafe.akka"         %%  "akka-remote"           % akkaVersion
  val akkaCluster       = "com.typesafe.akka"         %%  "akka-cluster"          % akkaVersion
  val slf4jApi          = "org.slf4j"                 %   "slf4j-api"             % slf4jVersion
  val slf4jnop          = "org.slf4j"                 %   "slf4j-nop"             % slf4jVersion
  val slf4jJul          = "org.slf4j"                 %   "jul-to-slf4j"          % slf4jVersion
  val slf4jLog4j        = "org.slf4j"                 %   "log4j-over-slf4j"      % slf4jVersion
  val scalazConcurrent  = "org.scalaz"                %%  "scalaz-concurrent"     % "7.1.0"
  val sigarLoader       = "io.kamon"                  %   "sigar-loader"          % "1.6.5-rev002"
  val h2                = "com.h2database"            %   "h2"                    % "1.4.182"
  val el                = "org.glassfish"             %   "javax.el"              % "3.0.0"
  val fluentdLogger     = "org.fluentd"               %%  "fluent-logger-scala"   % "0.5.1"
  val easyMock          = "org.easymock"              %   "easymock"              % "3.2"

  //play 2.3.x
  val play23            = "com.typesafe.play"         %%  "play"                  % play23Version
  val playWS23          = "com.typesafe.play"         %%  "play-ws"               % play23Version
  val playTest23        = "org.scalatestplus"         %%  "play"                  % "1.2.0"

  //play 2.4.x
  val play24            = "com.typesafe.play"         %%  "play"                  % play24Version
  val playWS24          = "com.typesafe.play"         %%  "play-ws"               % play24Version
  val playTest24        = "org.scalatestplus"         %%  "play"                  % "1.4.0-M2"
  val typesafeConfig    = "com.typesafe"              %   "config"                % "1.2.1"

  def compile   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
  def provided  (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
  def test      (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")
  def runtime   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "runtime")
  def container (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "container")
  def optional  (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile,optional")
}
