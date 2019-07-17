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

val kamonCore       = "io.kamon"      %% "kamon-core"     % "2.0.0-RC1"
val kamonTestkit    = "io.kamon"      %% "kamon-testkit"  % "2.0.0-RC1"

val el              = "org.glassfish" % "javax.el"        % "3.0.1-b11"

lazy val root = (project in file("."))
  .settings(noPublishing: _*)
  .aggregate(annotationApi, annotation)

val commonSettings = Seq(
    scalaVersion := "2.12.8",
    resolvers += Resolver.mavenLocal,
    crossScalaVersions := Seq("2.12.8", "2.11.12", "2.13.0"),
    assembleArtifact in assemblyPackageScala := false,
    assemblyMergeStrategy in assembly := {
        case s if s.startsWith("LICENSE") => MergeStrategy.discard
        case s if s.startsWith("about") => MergeStrategy.discard
        case x => (assemblyMergeStrategy in assembly).value(x)
    })

lazy val annotationApi = (project in file("kamon-annotation-api"))
  .settings(moduleName := "kamon-annotation-api", resolvers += Resolver.mavenLocal)
  .settings(crossPaths := false, autoScalaLibrary := false) // vanilla java
  .settings(publishMavenStyle := true)
  .settings(javacOptions in (Compile, doc) := Seq("-Xdoclint:none"))
  .settings(commonSettings: _*)

lazy val annotation = (project in file("kamon-annotation"))
  .enablePlugins(JavaAgent)
  .enablePlugins(AssemblyPlugin)
  .settings(moduleName := "kamon-annotation")
  .settings(commonSettings: _*)
  .settings(javaAgents += "io.kamon"  % "kanela-agent"  % "1.0.0-RC4"  % "compile;test")
  .settings(
      packageBin in Compile := assembly.value,
      assemblyExcludedJars in assembly := filterOut((fullClasspath in assembly).value, "slf4j-api", "config", "kamon-core"),
      assemblyShadeRules in assembly := Seq(
      ShadeRule.rename("javax.el.**"    -> "kamon.lib.@0").inAll,
      ShadeRule.rename("com.sun.el.**"    -> "kamon.lib.@0").inAll,
    ),
    libraryDependencies ++=
      compileScope(kamonCore, el) ++
      testScope(scalatest, logbackClassic, kamonTestkit)
  ).dependsOn(annotationApi)

def filterOut(classPath: Classpath, patterns: String*): Classpath = {
    classPath filter { file => patterns.exists(file.data.getPath.contains(_))}
}
