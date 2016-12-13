/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

import com.typesafe.sbt.SbtAspectj.aspectjSettings
import sbt.Keys._
import sbt._

object Build extends Build {

  val appName         = "Kamon-Scalatra-Example"
  val appVersion      = "1.0-SNAPSHOT"

  val resolutionRepos = Seq(
      "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
      "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases",
      "Kamon Repository Snapshots" at "http://snapshots.kamon.io"
    )

  val defaultSettings = Seq(
      scalaVersion := "2.11.6",
      resolvers ++= resolutionRepos,
      scalacOptions := Seq(
        "-encoding",
        "utf8",
        "-g:vars",
        "-feature",
        "-unchecked",
        "-deprecation",
        "-target:jvm-1.6",
        "-language:postfixOps",
        "-language:implicitConversions",
        "-Xlog-reflective-calls"
      ))

  val kamonVersion = "0.4.0"

  val dependencies = Seq(
    "io.kamon"    	          %% "kamon-core"           	  % kamonVersion,
    "io.kamon"    	          %% "kamon-scala"         	    % kamonVersion,
    "io.kamon"    	          %% "kamon-log-reporter"   	  % kamonVersion,
    "net.databinder.dispatch" %% "dispatch-core"            % "0.11.1",
    "org.scalatra" 	          %% "scalatra" 			          % "2.4.0-SNAPSHOT",
    "ch.qos.logback"          %  "logback-classic"          % "1.1.1"               % "runtime",
    "org.eclipse.jetty"       %  "jetty-webapp"             % "9.1.3.v20140225"     % "compile;runtime;",
    "org.eclipse.jetty.orbit" %  "javax.servlet"            % "3.0.0.v201112011016" % "runtime;provided;test" artifacts Artifact("javax.servlet", "jar", "jar")
    )

  val main = Project(appName, file(".")).settings(libraryDependencies ++= dependencies)
                                        .settings(defaultSettings: _*)
                                        .settings(aspectjSettings ++ AspectJ.aspectjSettings)
}
