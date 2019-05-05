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

val kamonCore             = "io.kamon"     %%   "kamon-core"              % "1.2.0-M1"
val kamonTestkit          = "io.kamon"     %%   "kamon-testkit"           % "1.2.0-M1"
val kanelaScalaExtension  = "io.kamon"     %%   "kanela-scala-extension"  % "0.0.14"

val scalazConcurrent  = "org.scalaz"    %%  "scalaz-concurrent" % "7.2.8"
val catsEffect        = "org.typelevel" %%  "cats-effect"       % "1.2.0"

resolvers in ThisBuild += Resolver.bintrayRepo("kamon-io", "snapshots")
resolvers in ThisBuild += Resolver.mavenLocal

lazy val `kamon-futures` = (project in file("."))
  .enablePlugins(JavaAgent)
  .settings(name := "kamon-futures")
  .settings(noPublishing: _*)
  .aggregate(
    `kamon-scala-future`,
    `kamon-twitter-future`,
    `kamon-scalaz-future`,
    `kamon-cats-effect`,
  )

lazy val `kamon-twitter-future` = (project in file("kamon-twitter-future"))
  .enablePlugins(JavaAgent)
  .settings(bintrayPackage := "kamon-futures")
  .settings(instrumentationSettings)
  .settings(
    libraryDependencies ++=
      compileScope(kamonCore, kanelaScalaExtension) ++
      providedScope(kanelaAgent, aspectJ) ++
      optionalScope(twitterDependency("core").value) ++
      testScope(scalatest, kamonTestkit, logbackClassic))

lazy val `kamon-scalaz-future` = (project in file("kamon-scalaz-future"))
  .enablePlugins(JavaAgent)
  .settings(bintrayPackage := "kamon-futures")
  .settings(instrumentationSettings)
  .settings(
    libraryDependencies ++=
      compileScope(kamonCore, kanelaScalaExtension) ++
      providedScope(kanelaAgent, aspectJ) ++
      optionalScope(scalazConcurrent) ++
      testScope(scalatest, kamonTestkit, logbackClassic))

lazy val `kamon-scala-future` = (project in file("kamon-scala-future"))
  .enablePlugins(JavaAgent)
  .settings(bintrayPackage := "kamon-futures")
  .settings(instrumentationSettings)
  .settings(
    libraryDependencies ++=
      compileScope(kamonCore, kanelaScalaExtension) ++
      providedScope(kanelaAgent, aspectJ) ++
      testScope(scalatest, kamonTestkit, logbackClassic))

lazy val `kamon-cats-effect` = (project in file("kamon-cats-io"))
  .enablePlugins(JavaAgent)
  .settings(bintrayPackage := "kamon-futures")
  .settings(instrumentationSettings)
  .settings(
    libraryDependencies ++=
      compileScope(kamonCore, kanelaScalaExtension) ++
        providedScope(kanelaAgent, aspectJ) ++
        optionalScope(catsEffect) ++
        testScope(scalatest, kamonTestkit, logbackClassic)
  )

def twitterDependency(moduleName: String) = Def.setting {
  scalaBinaryVersion.value match {
    case "2.10"           => "com.twitter" %% s"util-$moduleName" % "6.34.0"
    case "2.11" | "2.12"  => "com.twitter" %% s"util-$moduleName" % "6.40.0"
  }
}
