/* =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
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
import Tests._

val kamonCore             = "io.kamon"  %%  "kamon-core"                    % "2.0.0-RC1"
val kamonTestkit          = "io.kamon"  %%  "kamon-testkit"                 % "2.0.0-RC1"
val kamonInstrumentation  = "io.kamon"  %%  "kamon-instrumentation-common"  % "2.0.0-RC1"
val kanelaAgent           = "io.kamon"  %   "kanela-agent"                  % "1.0.0-RC3"

val guava         = "com.google.guava"  % "guava"  % "24.1-jre"

lazy val kamonExecutors = (project in file("."))
  .settings(noPublishing: _*)
  .settings(name := "kamon-executors")
  .aggregate(executors, benchmark)

val commonSettings = Seq(
  crossScalaVersions := List("2.11.12", "2.12.8", "2.13.0"),
  resolvers += Resolver.mavenLocal,
  resolvers += Resolver.bintrayRepo("kamon-io", "snapshots")
)

lazy val executors = (project in file("kamon-executors"))
  .settings(commonSettings: _*)
  .settings(
    fork in Test := true,
    moduleName := "kamon-executors",
    testGrouping in Test := groupByExperimental((definedTests in Test).value, kanelaAgentJar.value),
    libraryDependencies ++=
      compileScope(kamonCore, kamonInstrumentation) ++
      providedScope(kanelaAgent) ++
      testScope(scalatest, logbackClassic, kamonTestkit, guava)
  )

lazy val benchmark = (project in file("kamon-executors-bench"))
  .enablePlugins(JmhPlugin)
  .settings(noPublishing: _*)
  .settings(commonSettings: _*)
  .settings(
    fork in Test := true,
    moduleName := "kamon-executors-bench",
    resolvers += Resolver.mavenLocal,
    libraryDependencies ++= compileScope(kamonCore, kamonInstrumentation, kanelaAgent, guava)
  ).dependsOn(executors)


def groupByExperimental(tests: Seq[TestDefinition], kanelaJar: File): Seq[Group] = {
  val (stable, experimental) = tests.partition(t => t.name != "kamon.instrumentation.executor.CaptureContextOnSubmitInstrumentationSpec")

  val stableGroup = Group("stableTests", stable, SubProcess(
    ForkOptions().withRunJVMOptions(Vector(
      "-javaagent:" + kanelaJar.toString
    ))
  ))

  val experimentalGroup = Group("experimentalTests", experimental, SubProcess(
    ForkOptions().withRunJVMOptions(Vector(
      "-javaagent:" + kanelaJar.toString,
      "-Dkanela.modules.executors.enabled=false",
      "-Dkanela.modules.executors-capture-on-submit.enabled=true"
    ))
  ))

  Seq(stableGroup, experimentalGroup)
}
