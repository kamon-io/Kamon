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
import sbt.Tests.{Group, SubProcess}

val kamonCore       = "io.kamon" %% "kamon-core"         % "2.0.4"
val kamonTestkit    = "io.kamon" %% "kamon-testkit"      % "2.0.4"
val kamonScala      = "io.kamon" %% "kamon-scala-future" % "2.0.0"
val kamonExecutors  = "io.kamon" %% "kamon-executors"    % "2.0.0"
val kamonInstrument = "io.kamon" %% "kamon-instrumentation-common" % "2.0.0"
val kanelaAgent     =  "io.kamon" % "kanela-agent"       % "1.0.4"

val akka24Version = "2.4.20"
val akka25Version = "2.5.26"
val akka26Version = "2.6.0"

val akkaActor       = "com.typesafe.akka"   %% "akka-actor"             % akka25Version
val akkaTestkit     = "com.typesafe.akka"   %% "akka-testkit"           % akka25Version
val akkaSLF4J       = "com.typesafe.akka"   %% "akka-slf4j"             % akka25Version
val akkaRemote      = "com.typesafe.akka"   %% "akka-remote"            % akka25Version
val akkaCluster     = "com.typesafe.akka"   %% "akka-cluster"           % akka25Version
val akkaSharding    = "com.typesafe.akka"   %% "akka-cluster-sharding"  % akka25Version
val akkaProtobuf    = "com.typesafe.akka"   %% "akka-protobuf"          % akka25Version
val netty           = "io.netty"            %  "netty"                  % "3.10.6.Final"

kanelaAgentVersion in ThisBuild := "1.0.3"

lazy val root = Project("kamon-akka", file("."))
  .settings(noPublishing)
  .settings(crossScalaVersions := Nil)
  .aggregate(
    testCommon,
    testsOnAkka24,
    testsOnAkka25,
    testsOnAkka26,
    benchmarks,
    instrumentation,
    instrumentationCommon,
    instrumentation25,
    instrumentation26,
  )


lazy val instrumentationCommon = Project("instrumentation-common", file("instrumentation/common"))
  .settings(noPublishing: _*)
  .settings(
    scalacOptions += "-target:jvm-1.8",
    scalaVersion := "2.12.8",
    crossScalaVersions := Seq("2.11.12", "2.12.8", "2.13.1"),
    libraryDependencies ++=
      compileScope(kamonCore, kamonInstrument, kamonScala, kamonExecutors) ++
      providedScope(akkaActor, akkaRemote, akkaProtobuf, akkaCluster, akkaSharding, kanelaAgent))

lazy val instrumentation25 = Project("instrumentation-25", file("instrumentation/akka-2.5"))
  .settings(noPublishing: _*)
  .dependsOn(instrumentationCommon)
  .settings(
    scalacOptions += "-target:jvm-1.8",
    scalaVersion := "2.12.8",
    crossScalaVersions := Seq("2.11.12", "2.12.8", "2.13.1"),
    libraryDependencies ++=
      compileScope(kamonCore, kamonInstrument, kamonScala, kamonExecutors) ++
      providedScope(akkaActor, akkaRemote, akkaProtobuf, akkaCluster, akkaSharding, kanelaAgent))

lazy val instrumentation26 = Project("instrumentation-26", file("instrumentation/akka-2.6"))
  .settings(noPublishing: _*)
  .dependsOn(instrumentationCommon)
  .settings(
    scalacOptions += "-target:jvm-1.8",
    scalaVersion := "2.12.8",
    crossScalaVersions := Seq("2.12.8", "2.13.1"),
    libraryDependencies ++=
      compileScope(kamonCore, kamonInstrument, kamonScala, kamonExecutors) ++
      providedScope(onAkka26(akkaActor, akkaRemote, akkaProtobuf, akkaCluster, akkaSharding) :+ kanelaAgent: _*))


// These common modules contains all the stuff that can be reused between different Akka versions. They compile with
// Akka 2.4, but the actual modules for each Akka version are only using the sources from these project instead of the
// compiled classes. This is just to ensure that if there are any binary incompatible changes between Akka 2.4 and 2.5
// at the internal level, we will still be compiling and testing with the right versions.
//
lazy val instrumentation = Project("instrumentation", file("instrumentation"))
  .settings(
    moduleName := "kamon-akka",
    bintrayPackage := "kamon-akka",
    scalacOptions += "-target:jvm-1.8",
    scalaVersion := "2.12.8",
    crossScalaVersions := Seq("2.11.12", "2.12.8", "2.13.1"),
    libraryDependencies ++= compileScope(kamonCore, kamonInstrument, kamonScala, kamonExecutors),
    products in Compile := (Def.taskDyn {
      if (scalaBinaryVersion.value == "2.11") {
        Def.task {
          (products in Compile in instrumentationCommon).value ++
            (products in Compile in instrumentation25).value
        }
      } else {
        Def.task {
          (products in Compile in instrumentationCommon).value ++
            (products in Compile in instrumentation25).value ++
            (products in Compile in instrumentation26).value
        }
      }
    }).value)

lazy val testCommon = Project("test-common", file("test/common"))
  .dependsOn(instrumentation)
  .settings(noPublishing: _*)
  .settings(
    test := ((): Unit),
    testOnly := ((): Unit),
    testQuick := ((): Unit),
    scalaVersion := "2.12.8",
    crossScalaVersions := Seq("2.11.12", "2.12.8", "2.13.1"),
    libraryDependencies ++=
      compileScope(kamonCore, kamonInstrument, kamonScala, kamonExecutors) ++
      providedScope(akkaActor, kanelaAgent) ++
      testScope(scalatest, kamonTestkit, akkaTestkit, akkaSLF4J, logbackClassic))


lazy val testsOnAkka24 = Project("test-24", file("test/akka-2.4"))
  .dependsOn(instrumentation)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(noPublishing: _*)
  .settings(
    scalaVersion := "2.12.8",
    crossScalaVersions := Seq("2.11.12", "2.12.8"),
    testGrouping in Test := removeUnsupportedTests((definedTests in Test).value, kanelaAgentJar.value),
    unmanagedSourceDirectories in Test ++= (unmanagedSourceDirectories in Test in testCommon).value,
    unmanagedResourceDirectories in Test ++= (unmanagedResourceDirectories in Test in testCommon).value,
    libraryDependencies ++=
      providedScope(onAkka24(akkaActor), onAkka24(akkaRemote), onAkka24(akkaCluster), onAkka24(akkaSharding), kanelaAgent) ++
      testScope(scalatest, kamonTestkit, onAkka24(akkaTestkit), onAkka24(akkaSLF4J), logbackClassic))

lazy val testsOnAkka25 = Project("test-25", file("test/akka-2.5"))
  .dependsOn(instrumentation)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(noPublishing: _*)
  .settings(
    scalaVersion := "2.12.8",
    crossScalaVersions := Seq("2.11.12", "2.12.8", "2.13.1"),
    unmanagedSourceDirectories in Test ++= (unmanagedSourceDirectories in Test in testCommon).value,
    unmanagedResourceDirectories in Test ++= (unmanagedResourceDirectories in Test in testCommon).value,
    libraryDependencies ++=
      providedScope(akkaActor, akkaRemote, akkaCluster, akkaSharding, kanelaAgent) ++
      testScope(scalatest, kamonTestkit, akkaTestkit, akkaSLF4J, logbackClassic))

lazy val testsOnAkka26 = Project("test-26", file("test/akka-2.6"))
  .dependsOn(instrumentation)
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(noPublishing: _*)
  .settings(
    scalaVersion := "2.12.8",
    crossScalaVersions := Seq("2.12.8", "2.13.1"),
    testGrouping in Test := removeUnsupportedTests((definedTests in Test).value, kanelaAgentJar.value),
    unmanagedSourceDirectories in Test ++= (unmanagedSourceDirectories in Test in testCommon).value,
    unmanagedResourceDirectories in Test ++= (unmanagedResourceDirectories in Test in testCommon).value,
    libraryDependencies ++=
      providedScope(onAkka26(akkaActor, akkaRemote, akkaCluster, akkaSharding) ++ Seq(kanelaAgent, netty): _*) ++
      testScope(onAkka26(akkaTestkit, akkaSLF4J) ++ Seq(scalatest, kamonTestkit, logbackClassic): _*))

lazy val benchmarks = Project("benchmarks", file("bench"))
  .enablePlugins(JmhPlugin)
  .dependsOn(instrumentation)
  .settings(noPublishing: _*)
  .settings(
    scalaVersion := "2.12.8",
    crossScalaVersions := Seq("2.11.12", "2.12.8", "2.13.1"),
    libraryDependencies ++= compileScope(akkaActor, akkaRemote, akkaCluster, akkaSharding, kanelaAgent))

def onAkka24(moduleID: ModuleID): ModuleID =
  moduleID.withRevision(akka24Version)

def onAkka26(moduleIDs: ModuleID*): Seq[ModuleID] =
  moduleIDs.map(_.withRevision(akka26Version))

def removeUnsupportedTests(tests: Seq[TestDefinition], kanelaJar: File): Seq[Group] = {
  val excludedFeatures = Seq("sharding")

  Seq(
    new Group("tests", tests.filter(t => excludedFeatures.find(f => t.name.toLowerCase.contains(f)).isEmpty), SubProcess(
      ForkOptions().withRunJVMOptions(Vector(
        "-javaagent:" + kanelaJar.toString
      ))
    ))
  )
}
