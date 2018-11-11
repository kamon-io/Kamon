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
val kamonCore       = "io.kamon" %% "kamon-core"         % "1.1.3"
val kamonTestkit    = "io.kamon" %% "kamon-testkit"      % "1.1.3"
val kamonScala      = "io.kamon" %% "kamon-scala-future" % "1.0.2"
val kamonExecutors  = "io.kamon" %% "kamon-executors"    % "1.0.3"

val kanelaScalaExtension  = "io.kamon"  %%  "kanela-scala-extension"  % "0.0.14"

val `akka-2.4` = "2.4.20"
val `akka-2.5` = "2.5.13"

def akkaDependency(name: String, version: String) = {
  "com.typesafe.akka" %% s"akka-$name" % version
}

lazy val `kamon-akka` = (project in file("."))
    .settings(noPublishing: _*)
    .aggregate(kamonAkka24, kamonAkka25)


lazy val kamonAkka24 = Project("kamon-akka-24", file("kamon-akka-2.4.x"))
  .settings(Seq(
    bintrayPackage := "kamon-akka",
    moduleName := "kamon-akka-2.4",
    scalaVersion := "2.12.1",
    crossScalaVersions := Seq("2.11.8", "2.12.1"),
    resolvers += Resolver.bintrayRepo("kamon-io", "snapshots")))
  .enablePlugins(JavaAgent)
  .settings(javaAgents ++= resolveAgent)
  .settings(
    libraryDependencies ++=
      compileScope(akkaDependency("actor", `akka-2.4`), kamonCore, kamonScala, kamonExecutors) ++
      optionalScope(logbackClassic) ++
      testScope(scalatest, kamonTestkit, akkaDependency("testkit", `akka-2.4`), akkaDependency("slf4j", `akka-2.4`), logbackClassic))

lazy val kamonAkka25 = Project("kamon-akka-25", file("kamon-akka-2.5.x"))
  .settings(Seq(
    bintrayPackage := "kamon-akka",
    moduleName := "kamon-akka-2.5",
    scalaVersion := "2.12.1",
    crossScalaVersions := Seq("2.11.8", "2.12.1"),
    resolvers += Resolver.bintrayRepo("kamon-io", "snapshots")))
  .enablePlugins(JavaAgent)
  .settings(javaAgents ++= resolveAgent)
  .settings(publishArtifact in (Compile, packageDoc) := false)
  .settings(publishArtifact in packageDoc := false)
  .settings(sources in (Compile,doc) := Seq.empty)
  .settings(
    libraryDependencies ++=
      compileScope(akkaDependency("actor", `akka-2.5`), kamonCore, kamonScala, kamonExecutors, kanelaScalaExtension) ++
      optionalScope(logbackClassic) ++
      testScope(scalatest, kamonTestkit, akkaDependency("testkit", `akka-2.5`), akkaDependency("slf4j", `akka-2.5`), logbackClassic))

def resolveAgent: Seq[ModuleID] = {
    val agent = Option(System.getProperty("agent")).getOrElse("aspectj")
    if(agent.equalsIgnoreCase("kanela"))
        Seq("org.aspectj" % "aspectjweaver" % "1.9.1" % "compile", "io.kamon" % "kanela-agent" % "0.0.15" % "compile;test")
    else
        Seq("org.aspectj" % "aspectjweaver" % "1.9.1" % "compile;test", "io.kamon" % "kanela-agent" % "0.0.15" % "compile")
}
