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

val kamonCore        = "io.kamon"   %% "kamon-core"             % "2.0.0-ef6e364d6b7316aa22e5e7887de9ef1bd7a69316"
val kamonTestkit     = "io.kamon"   %% "kamon-testkit"          % "2.0.0-ef6e364d6b7316aa22e5e7887de9ef1bd7a69316"
val kanelaAgent      = "io.kamon"   %  "kanela-agent"           % "0.0.17"
val kanelaExtension  = "io.kamon"   %% "kanela-kamon-extension" % "2.0.0-ab59ea17b017e1809b8e46ebccdee45e4a21ebe8"
val scalazConcurrent = "org.scalaz" %% "scalaz-concurrent"      % "7.2.8"

resolvers in ThisBuild += Resolver.bintrayRepo("kamon-io", "snapshots")
resolvers in ThisBuild += Resolver.mavenLocal

lazy val `kamon-futures` = (project in file("."))
  .enablePlugins(JavaAgent)
  .settings(name := "kamon-futures")
  .settings(noPublishing: _*)
  .aggregate(`kamon-scala-future`, `kamon-twitter-future`, `kamon-scalaz-future`)


lazy val `kamon-twitter-future` = (project in file("kamon-twitter-future"))
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    bintrayPackage := "kamon-futures",
    libraryDependencies ++=
      providedScope(kamonCore, kanelaAgent, kanelaExtension, twitterDependency("core").value) ++
      testScope(scalatest, kamonTestkit, logbackClassic))

lazy val `kamon-scalaz-future` = (project in file("kamon-scalaz-future"))
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    bintrayPackage := "kamon-futures",
    libraryDependencies ++=
      providedScope(kamonCore, kanelaAgent, kanelaExtension, scalazConcurrent) ++
      testScope(scalatest, kamonTestkit, logbackClassic))

lazy val `kamon-scala-future` = (project in file("kamon-scala-future"))
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    bintrayPackage := "kamon-futures",
    libraryDependencies ++=
      providedScope(kamonCore, kanelaAgent, kanelaExtension) ++
      testScope(scalatest, kamonTestkit, logbackClassic))


def twitterDependency(moduleName: String) = Def.setting {
  scalaBinaryVersion.value match {
    case "2.10"           => "com.twitter" %% s"util-$moduleName" % "6.34.0"
    case "2.11" | "2.12"  => "com.twitter" %% s"util-$moduleName" % "6.40.0"
  }
}