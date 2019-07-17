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


val kamonCore       = "io.kamon" %% "kamon-core"         % "2.0.0-RC1"
val kamonTestkit    = "io.kamon" %% "kamon-testkit"      % "2.0.0-RC1"
val kamonScala      = "io.kamon" %% "kamon-scala-future" % "2.0.0-RC3"
val kamonExecutors  = "io.kamon" %% "kamon-executors"    % "2.0.0-RC3"
val kamonInstrument = "io.kamon" %% "kamon-instrumentation-common" % "2.0.0-RC2"
val kanelaAgent     =  "io.kamon" % "kanela-agent"       % "1.0.0-RC4"

val akka24Version = "2.4.20"
val akka25Version = "2.5.23"

val akkaActor       = "com.typesafe.akka" %% "akka-actor"    % "2.5.23"
val akkaTestkit     = "com.typesafe.akka" %% "akka-testkit"  % "2.5.23"
val akkaSLF4J       = "com.typesafe.akka" %% "akka-slf4j"    % "2.5.23"

// These common modules contains all the stuff that can be reused between different Akka versions. They compile with
// Akka 2.4, but the actual modules for each Akka version are only using the sources from these project instead of the
// compiled classes. This is just to ensure that if there are any binary incompatible changes between Akka 2.4 and 2.5
// at the internal level, we will still be compiling and testing with the right versions.
//
lazy val instrumentation = Project("instrumentation", file("kamon-akka"))
  .settings(
    name := "kamon-akka",
    moduleName := "kamon-akka",
    bintrayPackage := "kamon-akka",
    scalacOptions += "-target:jvm-1.8",
    crossScalaVersions := Seq("2.11.12", "2.12.8", "2.13.0"),
    libraryDependencies ++=
      compileScope(kamonCore, kamonInstrument, kamonScala, kamonExecutors) ++
      providedScope(akkaActor, kanelaAgent))

lazy val commonTests = Project("common-tests", file("kamon-akka-common-tests"))
  .dependsOn(instrumentation)
  .settings(noPublishing: _*)
  .settings(
    test := ((): Unit),
    testOnly := ((): Unit),
    testQuick := ((): Unit),
    crossScalaVersions := Seq("2.11.12", "2.12.8", "2.13.0"),
    libraryDependencies ++=
      compileScope(kamonCore, kamonInstrument, kamonScala, kamonExecutors) ++
      providedScope(akkaActor, kanelaAgent) ++
      testScope(scalatest, kamonTestkit, akkaTestkit, akkaSLF4J, logbackClassic))


lazy val testsOnAkka24 = Project("kamon-akka-tests-24", file("kamon-akka-tests-2.4"))
  .dependsOn(instrumentation)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(noPublishing: _*)
  .settings(
    name := "kamon-akka-tests-2.4",
    crossScalaVersions := Seq("2.11.12", "2.12.8"),
    unmanagedSourceDirectories in Test ++= (unmanagedSourceDirectories in Test in commonTests).value,
    unmanagedResourceDirectories in Test ++= (unmanagedResourceDirectories in Test in commonTests).value,
    libraryDependencies ++=
      providedScope(onAkka24(akkaActor), kanelaAgent) ++
      testScope(scalatest, kamonTestkit, onAkka24(akkaTestkit), onAkka24(akkaSLF4J), logbackClassic))

lazy val testsOnAkka25 = Project("kamon-akka-tests-25", file("kamon-akka-tests-2.5"))
  .dependsOn(instrumentation)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(noPublishing: _*)
  .settings(
    name := "kamon-akka-tests-2.5",
    crossScalaVersions := Seq("2.11.12", "2.12.8", "2.13.0"),
    unmanagedSourceDirectories in Test ++= (unmanagedSourceDirectories in Test in commonTests).value,
    unmanagedResourceDirectories in Test ++= (unmanagedResourceDirectories in Test in commonTests).value,
    libraryDependencies ++=
      providedScope(akkaActor, kanelaAgent) ++
      testScope(scalatest, kamonTestkit, akkaTestkit, akkaSLF4J, logbackClassic))

lazy val benchmarks = Project("benchmarks", file("kamon-akka-bench"))
  .enablePlugins(JmhPlugin)
  .dependsOn(instrumentation)
  .settings(noPublishing: _*)
  .settings(
    crossScalaVersions := Seq("2.11.12", "2.12.8", "2.13.0"),
    libraryDependencies ++= compileScope(akkaActor, kanelaAgent))

def onAkka24(moduleID: ModuleID): ModuleID =
  moduleID.withRevision(akka24Version)
