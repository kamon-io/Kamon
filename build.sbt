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


val kamonCore           = "io.kamon"            %% "kamon-core"               % "1.0.0-RC1"
val kamonTestkit        = "io.kamon"            %% "kamon-testkit"            % "1.0.0-RC1"
val scalaExtension      = "io.kamon"            %% "agent-scala-extension"    % "0.0.6-experimental"

val h2                  = "com.h2database"      % "h2"                        % "1.4.182"
val mariaDB             = "org.mariadb.jdbc"    % "mariadb-java-client"       % "1.5.9"
val hikariCP            = "com.zaxxer"          % "HikariCP" % "2.6.2"

lazy val root = (project in file("."))
  .enablePlugins(JavaAgent)
  .settings(name := "kamon-jdbc")
  .settings(javaAgents += "io.kamon"    % "kamon-agent"   % "0.0.6-experimental"  % "compile;test")
  .settings(resolvers += Resolver.bintrayRepo("kamon-io", "snapshots"))
  .settings(
      libraryDependencies ++=
        compileScope(kamonCore, scalaExtension) ++
        providedScope(hikariCP, mariaDB) ++
        testScope(h2, kamonTestkit, scalatest, slf4jApi, logbackClassic))