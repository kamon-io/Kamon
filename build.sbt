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
val kamonCore         = "io.kamon"     %%   "kamon-core"        % "1.0.0-RC1"
val kamonTestkit      = "io.kamon"     %%   "kamon-testkit"     % "1.0.0-RC1"
val scalazConcurrent  = "org.scalaz"   %%   "scalaz-concurrent" % "7.2.8"

lazy val root = (project in file("."))
  .settings(name := "kamon-scala")
  .settings(aspectJSettings: _*)
  .settings(
    libraryDependencies ++=
      compileScope(kamonCore, kamonTestkit) ++
      providedScope(aspectJ) ++
      optionalScope(scalazConcurrent, twitterDependency("core").value) ++
      testScope(scalatest, logbackClassic))

def twitterDependency(moduleName: String) = Def.setting {
  scalaBinaryVersion.value match {
    case "2.10"           => "com.twitter" %% s"util-$moduleName" % "6.34.0"
    case "2.11" | "2.12"  => "com.twitter" %% s"util-$moduleName" % "6.40.0"
  }
}
