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

import scala.xml.Node
import scala.xml.transform.{RewriteRule, RuleTransformer}

lazy val kamon = (project in file("."))
  .settings(moduleName := "kamon")
  .settings(noPublishing: _*)
  .aggregate(core, statusPage, testkit, tests, benchmarks)


lazy val core = (project in file("kamon-core"))
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(AssemblyPlugin)
  .enablePlugins(SbtProguard)
  .settings(commonSettings: _*)
  .settings(
    moduleName := "kamon-core",
    buildInfoKeys := Seq[BuildInfoKey](version),
    buildInfoPackage := "kamon.status",
    packageBin in Compile := (proguard in Proguard).value.head,
    proguardOptions in Proguard ++= Seq(
      "-dontnote", "-dontwarn", "-ignorewarnings", "-dontobfuscate", "-dontoptimize",
      "-keepattributes **",
      "-keep class kamon.Kamon { *; }",
      "-keep class kamon.context.** { *; }",
      "-keep class kamon.metric.** { *; }",
      "-keep class kamon.module.** { *; }",
      "-keep class kamon.status.** { *; }",
      "-keep class kamon.tag.** { *; }",
      "-keep class kamon.trace.** { *; }",
      "-keep class kamon.util.** { *; }",
    ),
    libraryDependencies ++= Seq(
      "com.typesafe"            %  "config"              % "1.3.1",
      "org.slf4j"               %  "slf4j-api"           % "1.7.25",
      "org.hdrhistogram"        %  "HdrHistogram"        % "2.1.9",
      "org.jctools"             %  "jctools-core"        % "2.1.1",
      "org.eclipse.collections" %  "eclipse-collections" % "9.2.0",
    )
  )

lazy val testkit = (project in file("kamon-testkit"))
  .settings(commonSettings: _*)
  .settings(
    moduleName := "kamon-testkit",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.0.1" % "test"
    )
  ).dependsOn(core)

lazy val statusPage = (project in file("kamon-status-page"))
  .enablePlugins(AssemblyPlugin)
  .settings(commonSettings: _*)
  .settings(
    moduleName := "kamon-status-page",
    packageBin in Compile := assembly.value,
    libraryDependencies ++= Seq(
      "org.nanohttpd" %  "nanohttpd" % "2.3.1",
      "com.grack"     %  "nanojson"  % "1.1"
    )
  ).dependsOn(core % "provided")


lazy val tests = (project in file("kamon-core-tests"))
  .settings(noPublishing: _*)
  .settings(commonSettings: _*)
  .settings(
    moduleName := "kamon-core-tests",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest"        % "3.0.1" % "test",
      "ch.qos.logback" % "logback-classic"  % "1.2.3" % "test"
    )
  ).dependsOn(testkit)


lazy val benchmarks = (project in file("kamon-core-bench"))
  .enablePlugins(JmhPlugin)
  .settings(moduleName := "kamon-core-bench")
  .settings(noPublishing: _*)
  .settings(commonSettings: _*)
  .dependsOn(core)


lazy val commonSettings = Seq(
  scalaVersion := "2.12.8",
  javacOptions += "-XDignore.symbol.file",
  fork in Test := true,
  resolvers += Resolver.mavenLocal,
  crossScalaVersions := Seq("2.12.8", "2.11.12", "2.10.7"),
  concurrentRestrictions in Global += Tags.limit(Tags.Test, 1),
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-Xfuture",
    "-language:implicitConversions", "-language:higherKinds", "-language:existentials", "-language:postfixOps",
    "-unchecked"
  ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2,10)) => Seq("-Yno-generic-signatures", "-target:jvm-1.7")
    case Some((2,11)) => Seq("-Ybackend:GenASM","-Ydelambdafy:method","-target:jvm-1.8")
    case Some((2,12)) => Seq("-opt:l:method")
    case _ => Seq.empty
  }),
  assembleArtifact in assemblyPackageScala := false,
  assemblyShadeRules in assembly := Seq(
    ShadeRule.rename("org.HdrHistogram.**"    -> "kamon.lib.@0").inAll,
    ShadeRule.rename("com.grack.nanojson.**"  -> "kamon.lib.@0").inAll,
    ShadeRule.rename("org.jctools.**"         -> "kamon.lib.@0").inAll,
    ShadeRule.rename("fi.iki.elonen.**"       -> "kamon.lib.@0").inAll,
    ShadeRule.rename("org.eclipse.**"         -> "kamon.lib.@0").inAll,
  ),
  assemblyExcludedJars in assembly := {
    val cp = (fullClasspath in assembly).value
    val excludedPackages = Seq("slf4j-api", "config")
    cp filter { file => excludedPackages.exists(file.data.getPath.contains(_))}
  },
  assemblyMergeStrategy in assembly := {
    case s if s.startsWith("LICENSE") => MergeStrategy.discard
    case s if s.startsWith("about") => MergeStrategy.discard
    case x => (assemblyMergeStrategy in assembly).value(x)
  },
  assemblyJarName in assembly := s"${moduleName.value}_${scalaBinaryVersion.value}-${version.value}.jar",
  pomPostProcess := { originalPom => {
    val shadedGroups = Seq("org.hdrhistogram", "org.jctools", "org.nanohttpd", "com.grack", "org.eclipse.collections")
    val filterShadedDependencies = new RuleTransformer(new RewriteRule {
      override def transform(n: Node): Seq[Node] = {
        if(n.label == "dependency") {
          val group = (n \ "groupId").text
          val artifact = (n \ "artifactId").text
          if (shadedGroups.find(eo => eo.equalsIgnoreCase(group)).nonEmpty && !artifact.startsWith("kamon-core")) Seq.empty else n
        } else n
      }
    })

    filterShadedDependencies(originalPom)
  }},
  proguardVersion in Proguard := "6.0.3",
  proguardInputs in Proguard := Seq(assembly.value),
  javaOptions in (Proguard, proguard) := Seq("-Xmx2G")
)