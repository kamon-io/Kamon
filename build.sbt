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


val kamonCore  = "io.kamon" %% "kamon-core" % "0.6.6"
val h2                = "com.h2database"            %   "h2"                    % "1.4.182"
val hikariCP = "com.zaxxer" % "HikariCP" % "2.6.0"


lazy val root = (project in file("."))
  .settings(name := "kamon-jdbc")
  .settings(aspectJSettings: _*)
  .settings(
      libraryDependencies ++=
        compileScope(kamonCore) ++
        providedScope(aspectJ, hikariCP) ++
        testScope(h2, scalatest, slf4jApi, logbackClassic))