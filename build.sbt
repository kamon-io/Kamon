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
val kamonCore       = "io.kamon" %% "kamon-core"         % "2.0.0-07ebec0a2acd1ecd22a6cf41cb770e8daf39e3cc"
val kamonTestkit    = "io.kamon" %% "kamon-testkit"      % "2.0.0-07ebec0a2acd1ecd22a6cf41cb770e8daf39e3cc"

val kamonScala      = "io.kamon" %% "kamon-scala-future" % "2.0.0-09bfd0aad353b0af5ef832b4d6b1a11061e87524"
val kamonExecutors  = "io.kamon" %% "kamon-executors"    % "2.0.0-79b985e963b339fe0fc66a6c5b9a9380d67d0b5e"
val kanelaExtension  = "io.kamon"   %% "kanela-kamon-extension" % "2.0.0-9c3842e61c90ecf3e269647edc356d6fabfba795"

val kanelaScalaExtension  = "io.kamon"  %%  "kanela-scala-extension"  % "0.0.14"
val kanelaAgent           =  "io.kamon" % "kanela-agent" % "0.0.19-SNAPSHOT" changing()

val `akka-2.5` = "2.5.13"

def akkaDependency(name: String, version: String) = {
  "com.typesafe.akka" %% s"akka-$name" % version
}

lazy val `kamon-akka` = (project in file("."))
  .settings(noPublishing: _*)
  .settings(scalacOptions += "-target:jvm-1.8")
  .aggregate(kamonAkka25)

lazy val kamonAkka25 = Project("kamon-akka-25", file("kamon-akka-2.5.x"))
  .settings(Seq(
    bintrayPackage := "kamon-akka",
    moduleName := "kamon-akka-2.5",
    resolvers += Resolver.bintrayRepo("kamon-io", "snapshots"),
    resolvers += Resolver.mavenLocal
  ))
  .enablePlugins(JavaAgent)
  .settings(javaAgents += kanelaAgent % "compile;test")
  .settings(publishArtifact in (Compile, packageDoc) := false)
  .settings(publishArtifact in packageDoc := false)
  .settings(sources in (Compile,doc) := Seq.empty)
  .settings(
    libraryDependencies ++=
      compileScope(akkaDependency("actor", `akka-2.5`), kamonCore, kamonScala, kamonExecutors, kanelaScalaExtension, kanelaExtension) ++
        optionalScope(logbackClassic) ++
        testScope(scalatest, kamonTestkit, akkaDependency("testkit", `akka-2.5`), akkaDependency("slf4j", `akka-2.5`), logbackClassic))

