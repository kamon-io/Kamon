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


val kamonCore           = "io.kamon"            %% "kamon-core"               % "2.0.0-M5"
val kamonTestkit        = "io.kamon"            %% "kamon-testkit"            % "2.0.0-M5"
val kanela              = "io.kamon"            %  "kanela-agent"             % "1.0.0-M3"

val slick               = "com.typesafe.slick"       %% "slick"                     % "3.2.3"
val h2                  = "com.h2database"            % "h2"                        % "1.4.182"
val mariaConnector      = "org.mariadb.jdbc"          % "mariadb-java-client"       % "2.2.6"
val mariaDB4j           = "ch.vorburger.mariaDB4j"    % "mariaDB4j"                 % "2.2.3"
val hikariCP            = "com.zaxxer"                % "HikariCP"                  % "2.6.2"

lazy val root = (project in file("."))
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    name := "kamon-jdbc",
    libraryDependencies ++=
      compileScope(kamonCore) ++
      providedScope(kanela, hikariCP, mariaConnector, slick) ++
      testScope(h2, mariaDB4j, kamonTestkit, scalatest, slf4jApi, logbackClassic))