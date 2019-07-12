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

val kamonCore        = "io.kamon"     %% "kamon-core"                   % "2.0.0-RC1"
val kamonTestkit     = "io.kamon"     %% "kamon-testkit"                % "2.0.0-RC1"
val kamonExecutors   = "io.kamon"     %% "kamon-executors"              % "2.0.0-RC2"
val kamonInstrument  = "io.kamon"     %% "kamon-instrumentation-common" % "2.0.0-RC1"
val kanelaAgent      = "io.kamon"     %  "kanela-agent"                 % "1.0.0-RC4"

val twitterUtilCore  = "com.twitter"   %% "util-core"                   % "6.40.0"
val scalazConcurrent = "org.scalaz"    %% "scalaz-concurrent"           % "7.2.28"
val catsEffect       = "org.typelevel" %%  "cats-effect"                % "1.2.0"

resolvers in ThisBuild += Resolver.bintrayRepo("kamon-io", "snapshots")
resolvers in ThisBuild += Resolver.mavenLocal
scalaVersion := "2.13.0"

lazy val `kamon-futures` = (project in file("."))
  .enablePlugins(JavaAgent)
  .settings(noPublishing: _*)
  .settings(
    name := "kamon-futures",
    crossScalaVersions := Nil
  ).aggregate(`kamon-scala-future`, `kamon-twitter-future`, `kamon-scalaz-future`, `kamon-cats-io`)


lazy val `kamon-twitter-future` = (project in file("kamon-twitter-future"))
  .enablePlugins(JavaAgent)
  .settings(noPublishing: _*)
  .settings(instrumentationSettings)
  .settings(
    bintrayPackage := "kamon-futures",
    libraryDependencies ++=
      compileScope(kamonCore) ++
      providedScope(twitterUtilCore, kamonExecutors) ++
      testScope(scalatest, kamonTestkit, logbackClassic))

lazy val `kamon-scalaz-future` = (project in file("kamon-scalaz-future"))
  .enablePlugins(JavaAgent)
  .settings(noPublishing: _*)
  .settings(instrumentationSettings)
  .settings(
    bintrayPackage := "kamon-futures",
    crossScalaVersions := Seq("2.11.12", "2.12.8", "2.13.0"),
    libraryDependencies ++=
      compileScope(kamonCore) ++
      providedScope(scalazConcurrent, kamonExecutors) ++
      testScope(scalatest, kamonTestkit, logbackClassic))

lazy val `kamon-scala-future` = (project in file("kamon-scala-future"))
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    crossScalaVersions := Seq("2.11.12", "2.12.8", "2.13.0"),
    bintrayPackage := "kamon-futures",
    libraryDependencies ++=
      compileScope(kamonCore, kamonInstrument) ++
      providedScope(kanelaAgent) ++
      testScope(scalatest, kamonTestkit, logbackClassic))

lazy val `kamon-cats-io` = (project in file("kamon-cats-io"))
  .enablePlugins(JavaAgent)
  .settings(noPublishing: _*)
  .settings(bintrayPackage := "kamon-futures")
  .settings(instrumentationSettings)
  .settings(
    libraryDependencies ++=
      compileScope(kamonCore, kamonInstrument) ++
      providedScope(catsEffect, kanelaAgent, kamonExecutors) ++
      testScope(scalatest, kamonTestkit, logbackClassic))
