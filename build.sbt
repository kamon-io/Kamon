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


val kamonCore           = "io.kamon"            %% "kamon-core"               % "2.0.0-d2d5cb18261e2c79b86340bf39524394700415e8"
val kamonTestkit        = "io.kamon"            %% "kamon-testkit"            % "2.0.0-d2d5cb18261e2c79b86340bf39524394700415e8"
val scalaExtension      = "io.kamon"            %% "kanela-scala-extension"   % "0.0.14"

val h2                  = "com.h2database"            % "h2"                        % "1.4.182"
val mariaConnector      = "org.mariadb.jdbc"          % "mariadb-java-client"       % "2.2.6"
val mariaDB4j           = "ch.vorburger.mariaDB4j"    % "mariaDB4j"                 % "2.2.3"
val hikariCP            = "com.zaxxer"                % "HikariCP"                  % "2.6.2"

lazy val root = (project in file("."))
  .enablePlugins(JavaAgent)
  .settings(name := "kamon-jdbc")
  .settings(scalaVersion := "2.12.6")
  .settings(  crossScalaVersions := Seq("2.12.6"/*, "2.11.12", "2.10.7"*/))
  .settings(javaAgents += "io.kamon"    % "kanela-agent"   % "0.0.18-SNAPSHOT"  % "compile;test")
  .settings(resolvers += Resolver.bintrayRepo("kamon-io", "snapshots"))
  .settings(resolvers += Resolver.mavenLocal)
  .settings(
      libraryDependencies ++=
        compileScope(kamonCore, scalaExtension) ++
        providedScope(hikariCP, mariaConnector) ++
        testScope(h2, mariaDB4j, kamonTestkit, scalatest, slf4jApi, logbackClassic))