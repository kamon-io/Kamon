/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

val play24Version     = "2.4.11"
val play25Version     = "2.5.19"
val play26Version     = "2.6.20"

val kamonCore         = "io.kamon"                  %%  "kamon-core"            % "1.1.4"
val kamonScala        = "io.kamon"                  %%  "kamon-scala-future"    % "1.0.0"
val kamonTestkit      = "io.kamon"                  %%  "kamon-testkit"         % "1.0.0"

//play 2.4.x
val play24            = "com.typesafe.play"         %%  "play"                  % play24Version
val playWS24          = "com.typesafe.play"         %%  "play-ws"               % play24Version
val playTest24        = "com.typesafe.play"         %%  "play-test"             % play24Version
val scalatestplus24   = "org.scalatestplus"         %%  "play"                  % "1.4.0"
val typesafeConfig    = "com.typesafe"              %   "config"                % "1.2.1"

//play 2.5.x
val play25            = "com.typesafe.play"         %%  "play"                  % play25Version
val playWS25          = "com.typesafe.play"         %%  "play-ws"               % play25Version
val playTest25        = "com.typesafe.play"         %%  "play-test"             % play25Version
val scalatestplus25   = "org.scalatestplus.play"    %%  "scalatestplus-play"    % "2.0.1"

//play 2.6.x
val play26            = "com.typesafe.play"         %%  "play"                  % play26Version
val playNetty26       = "com.typesafe.play"         %%  "play-netty-server"     % play26Version
val playAkkaHttp26    = "com.typesafe.play"         %%  "play-akka-http-server" % play26Version
val playWS26          = "com.typesafe.play"         %%  "play-ws"               % play26Version
val playLogBack26     = "com.typesafe.play"         %%  "play-logback"          % play26Version
val playTest26        = "com.typesafe.play"         %%  "play-test"             % play26Version
val scalatestplus26   = "org.scalatestplus.play"    %%  "scalatestplus-play"    % "3.1.2"


lazy val kamonPlay = Project("kamon-play", file("."))
  .settings(noPublishing: _*)
  .aggregate(kamonPlay24, kamonPlay25, kamonPlay26)


lazy val kamonPlay24 = Project("kamon-play-24", file("kamon-play-2.4.x"))
  .enablePlugins(JavaAgent)
  .settings(Seq(
      bintrayPackage := "kamon-play",
      moduleName := "kamon-play-2.4",
      scalaVersion := "2.11.12",
      crossScalaVersions := Seq("2.11.12"),
    testGrouping in Test := singleTestPerJvm((definedTests in Test).value, (javaOptions in Test).value)))
  .settings(javaAgents += "org.aspectj" % "aspectjweaver"  % "1.9.2"  % "compile;test")
  .settings(resolvers += Resolver.bintrayRepo("kamon-io", "snapshots"))
  .settings(
    libraryDependencies ++=
      compileScope(kamonCore, kamonScala) ++
      providedScope(aspectJ, play24, playWS24, typesafeConfig) ++
      testScope(playTest24, scalatestplus24))

lazy val kamonPlay25 = Project("kamon-play-25", file("kamon-play-2.5.x"))
  .enablePlugins(JavaAgent)
  .settings(Seq(
      bintrayPackage := "kamon-play",
      moduleName := "kamon-play-2.5",
      scalaVersion := "2.11.12",
      crossScalaVersions := Seq("2.11.12"),
      testGrouping in Test := singleTestPerJvm((definedTests in Test).value, (javaOptions in Test).value)))
  .settings(javaAgents += "org.aspectj" % "aspectjweaver"  % "1.9.2"  % "compile;test")
  .settings(resolvers += Resolver.bintrayRepo("kamon-io", "snapshots"))
  .settings(
    libraryDependencies ++=
      compileScope(kamonCore, kamonScala) ++
      providedScope(aspectJ, play25, playWS25, typesafeConfig) ++
      testScope(playTest25, scalatestplus25, kamonTestkit, logbackClassic))


lazy val kamonPlay26 = Project("kamon-play-26", file("kamon-play-2.6.x"))
  .enablePlugins(JavaAgent)
  .settings(Seq(
    bintrayPackage := "kamon-play",
    moduleName := "kamon-play-2.6",
    scalaVersion := "2.12.8",
    crossScalaVersions := Seq("2.11.12", "2.12.8"),
    testGrouping in Test := singleTestPerJvm((definedTests in Test).value, (javaOptions in Test).value)))
  .settings(javaAgents += "org.aspectj" % "aspectjweaver"  % "1.9.2"  % "compile;test")
  .settings(resolvers += Resolver.bintrayRepo("kamon-io", "snapshots"))
  .settings(
    libraryDependencies ++=
      compileScope(play26, playNetty26, playAkkaHttp26, playWS26, kamonCore, kamonScala) ++
      providedScope(aspectJ, typesafeConfig) ++
      testScope(playTest26, scalatestplus26, playLogBack26, kamonTestkit))

import sbt.Tests._
def singleTestPerJvm(tests: Seq[TestDefinition], jvmSettings: Seq[String]): Seq[Group] =
  tests map { test =>
    Group(
      name = test.name,
      tests = Seq(test),
      runPolicy = SubProcess(ForkOptions(javaHome = Option.empty[File], outputStrategy = Option.empty[OutputStrategy], bootJars = Vector(), workingDirectory = Option.empty[File], runJVMOptions = jvmSettings.toVector, connectInput = false, envVars = Map.empty[String, String])))
  }
