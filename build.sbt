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


val kamonCore           = "io.kamon" %% "kamon-core"                    % "2.0.0-RC1"
val kamonTestKit        = "io.kamon" %% "kamon-testkit"                 % "2.0.0-RC1"
val kamonCommon         = "io.kamon" %% "kamon-instrumentation-common"  % "2.0.0-RC2"
val kamonAkka25         = "io.kamon" %% "kamon-akka"                    % "2.0.0-RC3"

val akkaHttpJson        = "de.heikoseeberger" %% "akka-http-json4s"     % "1.18.1"
val json4sNative        = "org.json4s"        %% "json4s-native"        % "3.5.3"
val http25              = "com.typesafe.akka" %% "akka-http"            % "10.1.8"
val httpTestKit25       = "com.typesafe.akka" %% "akka-http-testkit"    % "10.1.8"
val stream25            = "com.typesafe.akka" %% "akka-stream"          % "2.5.22"


lazy val root = (project in file("."))
  .settings(noPublishing: _*)
  .settings(crossScalaVersions := Nil)
  .aggregate(kamonAkkaHttp25)

lazy val kamonAkkaHttp25 = Project("kamon-akka-http", file("kamon-akka-http"))
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings: _*)
  .settings(Seq(
    name := "kamon-akka-http",
    moduleName := "kamon-akka-http",
    bintrayPackage := "kamon-akka-http",
    scalaVersion := "2.12.8",
    fork in test := true,
    resolvers += Resolver.mavenLocal,
    crossScalaVersions := Seq("2.11.12", "2.12.8")),
    libraryDependencies ++=
      compileScope(kamonCore, kamonAkka25, kamonCommon) ++
      providedScope(("io.kamon" % "kanela-agent" % "1.0.0-M3"), http25, stream25) ++
      testScope(httpTestKit25, scalatest, slf4jApi, slf4jnop, kamonTestKit, akkaHttpJson, json4sNative))
