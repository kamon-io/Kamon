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
val play25Version     = "2.5.18"
val play26Version     = "2.6.12"

val kamonCore         = "io.kamon"                  %%  "kamon-core"            % "1.1.0"
val kamonScala        = "io.kamon"                  %%  "kamon-scala-future"    % "1.0.0"
val kamonTestkit      = "io.kamon"                  %%  "kamon-testkit"         % "1.0.0"

//play 2.4.x
val play24            = "com.typesafe.play"         %%  "play"                  % play24Version
val playWS24          = "com.typesafe.play"         %%  "play-ws"               % play24Version
val playTest24        = "org.scalatestplus"         %%  "play"                  % "1.4.0"
val typesafeConfig    = "com.typesafe"              %   "config"                % "1.2.1"

//play 2.5.x
val play25            = "com.typesafe.play"         %%  "play"                  % play25Version
val playWS25          = "com.typesafe.play"         %%  "play-ws"               % play25Version
val playTest25        = "org.scalatestplus.play"    %%  "scalatestplus-play"    % "2.0.0"

//play 2.6.x
val play26            = "com.typesafe.play"         %%  "play"                  % play26Version
val playNetty26       = "com.typesafe.play"         %%  "play-netty-server"     % play26Version
val playWS26          = "com.typesafe.play"         %%  "play-ws"               % play26Version
val playLogBack26     = "com.typesafe.play"         %%  "play-logback"          % play26Version
val playTest26        = "org.scalatestplus.play"    %%  "scalatestplus-play"    % "3.0.0"
val akkaHttp          = "com.typesafe.akka"         %%  "akka-http-core"        % "10.0.8"


lazy val kamonPlay = Project("kamon-play", file("."))
  .settings(noPublishing: _*)
  .aggregate(kamonPlay24, kamonPlay25, kamonPlay26)


lazy val kamonPlay24 = Project("kamon-play-24", file("kamon-play-2.4.x"))
  .enablePlugins(JavaAgent)
  .settings(Seq(
      bintrayPackage := "kamon-play",
      moduleName := "kamon-play-2.4",
      scalaVersion := "2.11.8",
      crossScalaVersions := Seq("2.10.6", "2.11.8"),
    testGrouping in Test := singleTestPerJvm((definedTests in Test).value, (javaOptions in Test).value)))
  .settings(javaAgents += "org.aspectj" % "aspectjweaver"  % "1.8.10"  % "compile;test")
  .settings(resolvers += Resolver.bintrayRepo("kamon-io", "snapshots"))
  .settings(
    libraryDependencies ++=
      compileScope(play24, playWS24, kamonCore, kamonScala) ++
      providedScope(aspectJ, typesafeConfig) ++
      testScope(playTest24))

lazy val kamonPlay25 = Project("kamon-play-25", file("kamon-play-2.5.x"))
  .enablePlugins(JavaAgent)
  .settings(Seq(
      bintrayPackage := "kamon-play",
      moduleName := "kamon-play-2.5",
      scalaVersion := "2.11.8",
      crossScalaVersions := Seq("2.11.8"),
      testGrouping in Test := singleTestPerJvm((definedTests in Test).value, (javaOptions in Test).value)))
  .settings(javaAgents += "org.aspectj" % "aspectjweaver"  % "1.8.10"  % "compile;test")
  .settings(resolvers += Resolver.bintrayRepo("kamon-io", "snapshots"))
  .settings(
    libraryDependencies ++=
      compileScope(play25, playWS25, kamonCore, kamonScala) ++
      providedScope(aspectJ, typesafeConfig) ++
      testScope(playTest25, kamonTestkit, logbackClassic))


lazy val kamonPlay26 = Project("kamon-play-26", file("kamon-play-2.6.x"))
  .enablePlugins(JavaAgent)
  .settings(Seq(
    bintrayPackage := "kamon-play",
    moduleName := "kamon-play-2.6",
    scalaVersion := "2.12.4",
    crossScalaVersions := Seq("2.11.12", "2.12.4"),
    testGrouping in Test := singleTestPerJvm((definedTests in Test).value, (javaOptions in Test).value)))
  .settings(javaAgents += "org.aspectj" % "aspectjweaver"  % "1.8.10"  % "compile;test")
  .settings(resolvers += Resolver.bintrayRepo("kamon-io", "snapshots"))
  .settings(
    libraryDependencies ++=
      compileScope(play26, playNetty26, playWS26, kamonCore, kamonScala) ++
        providedScope(aspectJ, typesafeConfig, akkaHttp) ++
        testScope(playTest26, playLogBack26, kamonTestkit))

import sbt.Tests._
def singleTestPerJvm(tests: Seq[TestDefinition], jvmSettings: Seq[String]): Seq[Group] =
  tests map { test =>
    Group(
      name = test.name,
      tests = Seq(test),
      runPolicy = SubProcess(ForkOptions(runJVMOptions = jvmSettings)))
  }

enableProperCrossScalaVersionTasks
