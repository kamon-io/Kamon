/*
 * =========================================================================================
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

resolvers += Resolver.mavenLocal
resolvers += Resolver.bintrayRepo("hseeberger", "maven")


val kamonCore           = "io.kamon" %% "kamon-core"         % "2.0.0-M4"
val kamonTestKit        = "io.kamon" %% "kamon-testkit"         % "2.0.0-M4"
val kamonAkka25         = "io.kamon" %% "kamon-akka-2.5"        % "2.0.0-eab1434dc86cc9c480d93033a43384496781e554" changing()
val akkaHttpJson        = "de.heikoseeberger" %% "akka-http-json4s" % "1.18.1"
val json4sNative        = "org.json4s" %% "json4s-native" % "3.5.3"

val http25         = "com.typesafe.akka" %% "akka-http"          % "10.1.8"
val stream25       = "com.typesafe.akka" %% "akka-stream"        % "2.5.22"
val httpTestKit25  = "com.typesafe.akka" %% "akka-http-testkit"  % "10.1.8"


lazy val `kamon-akka-http` = (project in file("."))
  .aggregate(kamonAkkaHttp25)
  .settings(noPublishing: _*)
  .settings(crossScalaVersions := Nil)

lazy val kamonAkkaHttp25 = Project("kamon-akka-http-25", file("kamon-akka-http-2.5.x"))
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings: _*)
  .settings(Seq(
    name := "kamon-akka-http-25",
    moduleName := "kamon-akka-http-2.5",
    bintrayPackage := "kamon-akka-http",
    scalaVersion := "2.12.8",
      fork in test := true,
    resolvers += Resolver.mavenLocal,
    crossScalaVersions := Seq("2.11.12", "2.12.8")),
    libraryDependencies ++=
      compileScope(kamonCore, kamonAkka25) ++
      providedScope(("io.kamon" % "kanela-agent"       % "1.0.0-M2" changing()), http25, stream25) ++
      testScope(httpTestKit25, scalatest, slf4jApi, slf4jnop, kamonTestKit, akkaHttpJson, json4sNative))
