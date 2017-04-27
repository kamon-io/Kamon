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

lazy val kamon = (project in file("."))
  .settings(moduleName := "kamon")
  .settings(noPublishing: _*)
  .aggregate(core, testkit)


lazy val core = (project in file("kamon-core"))
  .settings(moduleName := "kamon-core")
  .settings(
    scalaVersion := "2.12.1",
    resolvers += Resolver.mavenLocal,
    libraryDependencies ++= Seq(
      "com.typesafe"     % "config"          % "1.3.1",
      "org.slf4j"        % "slf4j-api"       % "1.7.7",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
      "org.hdrhistogram" % "HdrHistogram"    % "2.1.9",
      "io.opentracing"   % "opentracing-api" % "0.21.1-SNAPSHOT",

      "uk.org.lidalia" % "slf4j-test" % "1.1.0",
      "org.scalatest" %% "scalatest" % "3.0.1" % "test"
    )
  )

lazy val testkit = (project in file("kamon-testkit"))
  .settings(moduleName := "kamon-testkit", resolvers += Resolver.mavenLocal)
  .settings(
    libraryDependencies ++=
      compileScope(akkaDependency("actor").value, akkaDependency("testkit").value) ++
      providedScope(aspectJ) ++
      testScope(slf4jApi, slf4jnop)
  ).dependsOn(core)

//
//lazy val coreTests = (project in file("kamon-core-tests"))
//  .settings(moduleName := "kamon-core-tests", resolvers += Resolver.mavenLocal)
//  .settings(noPublishing: _*)
//  .settings(
//   libraryDependencies ++=
//     compileScope(akkaDependency("actor").value, akkaDependency("testkit").value) ++
//       providedScope(aspectJ) ++
//       testScope(slf4jApi, slf4jnop)
//  ).dependsOn(testkit )
