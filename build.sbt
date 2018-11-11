/* =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
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

val kamonCore       = "io.kamon" %% "kamon-core"              % "1.2.0-M1"
val kamonTestkit    = "io.kamon" %% "kamon-testkit"           % "1.2.0-M1"

val kanelaScala     = "io.kamon" %% "kanela-scala-extension"  % "0.0.14"

val guava           = "com.google.guava"  % "guava"  % "24.1-jre"

lazy val root = (project in file("."))
  .settings(noPublishing: _*)
  .aggregate(executors, benchmark)


val commonSettings = Seq(
  scalaVersion := "2.12.6",
  resolvers += Resolver.mavenLocal,
  crossScalaVersions := Seq("2.12.6", "2.11.12", "2.10.7")
)

lazy val executors = (project in file("kamon-executors"))
  .enablePlugins(JavaAgent)
  .settings(moduleName := "kamon-executors")
  .settings(commonSettings: _*)
  .settings(javaAgents += "io.kamon"  % "kanela-agent"  % "0.0.15"  % "compile;test")
  .settings(
    libraryDependencies ++=
      compileScope(kamonCore, kanelaScala) ++
      testScope(scalatest, logbackClassic, kamonTestkit, guava)
  )

lazy val benchmark = (project in file("kamon-executors-bench"))
  .enablePlugins(JmhPlugin)
  .settings(
    moduleName := "kamon-executors-bench",
    resolvers += Resolver.mavenLocal,
    fork in Test := true)
  .settings(noPublishing: _*)
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= compileScope(guava))
  .dependsOn(executors)