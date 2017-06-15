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

resolvers += Resolver.bintrayRepo("kamon-io", "snapshots")
val kamonCore = "io.kamon" %% "kamon-core" % "1.0.0-RC1-8aa64c486f2ad1f31a5f27a0780f8c43f31b7f8c"

lazy val root = (project in file("."))
  .settings(name := "kamon-executors")
  .settings(
      libraryDependencies ++=
        compileScope(kamonCore) ++
        testScope(scalatest, logbackClassic))