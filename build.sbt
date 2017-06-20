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

val kamonCore = "io.kamon"  %%  "kamon-core"  % "1.0.0-RC1-1d98b9e8a397acf8b6f6f55a3fd5189eb72740ba"

name := "kamon-statsd"

libraryDependencies ++=
  compileScope(kamonCore) ++
  testScope(scalatest, slf4jApi, logbackClassic)


