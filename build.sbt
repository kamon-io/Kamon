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


val kamonCore       = "io.kamon" %% "kamon-core"         % "2.0.0-M6"
val kamonTestkit    = "io.kamon" %% "kamon-testkit"      % "2.0.0-M6"
val kamonScala      = "io.kamon" %% "kamon-scala-future" % "2.0.0-M2"
val kamonExecutors  = "io.kamon" %% "kamon-executors"    % "2.0.0-M2"
val kamonInstrument = "io.kamon" %% "kamon-instrumentation-common" % "2.0.0-M2"
val kanelaAgent     =  "io.kamon" % "kanela-agent"       % "1.0.0-M3"

val akka24Version = "2.4.20"
val akka25Version = "2.5.23"

val akkaActor       = "com.typesafe.akka" %% "akka-actor"    % "2.5.23"
val akkaTestkit     = "com.typesafe.akka" %% "akka-testkit"  % "2.5.23"
val akkaSLF4J       = "com.typesafe.akka" %% "akka-slf4j"    % "2.5.23"

lazy val `kamon-akka` = (project in file("."))
  .settings(noPublishing: _*)
  .aggregate(common, commonTests, kamonAkka24, kamonAkka25, kamonAkkaTests24, kamonAkkaTests25)

// These common modules contains all the stuff that can be reused between different Akka versions. They compile with
// Akka 2.4, but the actual modules for each Akka version are only using the sources from these project instead of the
// compiled classes. This is just to ensure that if there are any binary incompatible changes between Akka 2.4 and 2.5
// at the internal level, we will still be compiling and testing with the right versions.
//
lazy val common = Project("kamon-akka-common", file("kamon-akka-common"))
  .settings(noPublishing: _*)
  .settings(
    libraryDependencies ++=
      compileScope(kamonCore, kamonInstrument, kamonScala, kamonExecutors) ++
      providedScope(onAkka24(akkaActor), kanelaAgent))

lazy val commonTests = Project("kamon-akka-common-tests", file("kamon-akka-common-tests"))
  .dependsOn(common)
  .settings(noPublishing: _*)
  .settings(
    test := ((): Unit),
    libraryDependencies ++=
      compileScope(kamonCore, kamonInstrument, kamonScala, kamonExecutors) ++
      providedScope(onAkka24(akkaActor), kanelaAgent) ++
      testScope(scalatest, kamonTestkit, onAkka24(akkaTestkit), onAkka24(akkaSLF4J), logbackClassic))


lazy val kamonAkka24 = Project("kamon-akka-24", file("kamon-akka-2.4"))
  .settings(
    name := "kamon-akka-2.4",
    moduleName := "kamon-akka-2.4",
    bintrayPackage := "kamon-akka",
    scalacOptions += "-target:jvm-1.8",
    unmanagedSourceDirectories in Compile ++= (unmanagedSourceDirectories in Compile in common).value,
    unmanagedResourceDirectories in Compile ++= (unmanagedResourceDirectories in Compile in common).value,
    libraryDependencies ++=
      compileScope(kamonCore, kamonInstrument, kamonScala, kamonExecutors) ++
      providedScope(onAkka24(akkaActor), kanelaAgent))

lazy val kamonAkka25 = Project("kamon-akka-25", file("kamon-akka-2.5"))
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    bintrayPackage := "kamon-akka",
    name := "kamon-akka-2.5",
    moduleName := "kamon-akka-2.5",
    scalacOptions += "-target:jvm-1.8",
    unmanagedSourceDirectories in Compile ++= (unmanagedSourceDirectories in Compile in common).value,
    unmanagedResourceDirectories in Compile ++= (unmanagedResourceDirectories in Compile in common).value,
    libraryDependencies ++=
      compileScope(kamonCore, kamonInstrument, kamonScala, kamonExecutors) ++
      providedScope(akkaActor, kanelaAgent) ++
      testScope(scalatest, kamonTestkit, akkaTestkit, akkaSLF4J, logbackClassic))


lazy val kamonAkkaTests24 = Project("kamon-akka-tests-24", file("kamon-akka-tests-2.4"))
  .dependsOn(kamonAkka24)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(noPublishing: _*)
  .settings(
    name := "kamon-akka-tests-2.4",
    unmanagedSourceDirectories in Test ++= (unmanagedSourceDirectories in Test in commonTests).value,
    unmanagedResourceDirectories in Test ++= (unmanagedResourceDirectories in Test in commonTests).value,
    libraryDependencies ++=
      providedScope(onAkka24(akkaActor), kanelaAgent) ++
      testScope(scalatest, kamonTestkit, onAkka24(akkaTestkit), onAkka24(akkaSLF4J), logbackClassic))

lazy val kamonAkkaTests25 = Project("kamon-akka-tests-25", file("kamon-akka-tests-2.5"))
  .dependsOn(kamonAkka25)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(noPublishing: _*)
  .settings(
    name := "kamon-akka-tests-2.5",
    unmanagedSourceDirectories in Test ++= (unmanagedSourceDirectories in Test in commonTests).value,
    unmanagedResourceDirectories in Test ++= (unmanagedResourceDirectories in Test in commonTests).value,
    libraryDependencies ++=
      providedScope(akkaActor, kanelaAgent) ++
      testScope(scalatest, kamonTestkit, akkaTestkit, akkaSLF4J, logbackClassic))

lazy val kamonAkkaBench25 = Project("kamon-akka-bench", file("kamon-akka-bench"))
  .enablePlugins(JmhPlugin)
  .dependsOn(kamonAkka25)
  .settings(noPublishing: _*)

def onAkka24(moduleID: ModuleID): ModuleID =
  moduleID.withRevision(akka24Version)