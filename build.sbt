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


lazy val kamon = (project in file("."))
  .settings(moduleName := "kamon")
  .settings(noPublishing: _*)
  .aggregate(core, testkit, coreTests)

val commonSettings = Seq(
  scalaVersion := "2.12.4",
  javacOptions += "-XDignore.symbol.file",
  resolvers += Resolver.mavenLocal,
  crossScalaVersions := Seq("2.12.4", "2.11.8", "2.10.6"),
  concurrentRestrictions in Global += Tags.limit(Tags.Test, 1),
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-Xfuture",
    "-language:implicitConversions", "-language:higherKinds", "-language:existentials", "-language:postfixOps",
    "-unchecked"
  ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2,10)) => Seq("-Yno-generic-signatures", "-target:jvm-1.7")
    case Some((2,11)) => Seq("-Ybackend:GenBCode","-Ydelambdafy:method","-target:jvm-1.8")
    case Some((2,12)) => Seq("-opt:l:method")
    case _ => Seq.empty
  })
)

lazy val core = (project in file("kamon-core"))
  .settings(moduleName := "kamon-core")
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe"     %  "config"          % "1.3.1",
      "org.slf4j"        %  "slf4j-api"       % "1.7.25",
      "org.hdrhistogram" %  "HdrHistogram"    % "2.1.9",
      "com.lihaoyi"      %% "fansi"           % "0.2.4"
    )
  )

lazy val testkit = (project in file("kamon-testkit"))
  .settings(moduleName := "kamon-testkit")
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.0.1"
    )
  ).dependsOn(core)


lazy val coreTests = (project in file("kamon-core-tests"))
  .settings(
    moduleName := "kamon-core-tests",
    resolvers += Resolver.mavenLocal,
    fork in Test := true)
  .settings(noPublishing: _*)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.0.1" % "test",
      "ch.qos.logback" % "logback-classic" % "1.2.2" % "test"
    )
  ).dependsOn(testkit)
