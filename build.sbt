import scala.xml.Node
import scala.xml.transform.{RewriteRule, RuleTransformer}

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


lazy val kamon = (project in file("."))
  .settings(moduleName := "kamon")
  .settings(noPublishing: _*)
  .aggregate(core, testkit, coreTests, coreBench)

val commonSettings = Seq(
  scalaVersion := "2.12.6",
  javacOptions += "-XDignore.symbol.file",
  resolvers += Resolver.mavenLocal,
  crossScalaVersions := Seq("2.12.6", "2.11.8", "2.10.6"),
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
    case Some((2,11)) => Seq("-Ybackend:GenBCode","-Ydelambdafy:method","-target:jvm-1.8")
    case Some((2,12)) => Seq("-opt:l:method")
    case _ => Seq.empty
  })
)

lazy val core = (project in file("kamon-core"))
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(AssemblyPlugin)
  .settings(commonSettings: _*)
  .settings(moduleName := "kamon-core")
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](version),
    buildInfoPackage := "kamon.status",
    libraryDependencies ++= Seq(
      "com.typesafe"     %  "config"              % "1.3.1",
      "org.hdrhistogram" %  "HdrHistogram"        % "2.1.9",
      "org.jctools"      %  "jctools-core"        % "2.1.1",
      "org.nanohttpd"    %  "nanohttpd"           % "2.3.1",
      "com.grack"        %  "nanojson"            % "1.1",
      "org.slf4j"        %  "slf4j-api"           % "1.7.25"
    ),
    assembleArtifact in assemblyPackageScala := false,
    assemblyShadeRules in assembly := Seq(
      ShadeRule.rename("org.HdrHistogram.**"    -> "kamon.lib.@0").inAll,
      ShadeRule.rename("com.grack.nanojson.**"  -> "kamon.lib.@0").inAll,
      ShadeRule.rename("org.jctools.**"         -> "kamon.lib.@0").inAll,
      ShadeRule.rename("fi.iki.elonen.**"       -> "kamon.lib.@0").inAll,
    ),
    assemblyExcludedJars in assembly := {
      val cp = (fullClasspath in assembly).value
      val excludedPackages = Seq("slf4j-api","config")
      cp filter { file => excludedPackages.exists(file.data.getName.startsWith(_))}
    },
    packageBin in Compile := assembly.value,
    assemblyJarName in assembly := s"${moduleName.value}_${scalaBinaryVersion.value}-${version.value}.jar",
    pomPostProcess := { originalPom => {
      val shadedGroups = Seq("org.hdrhistogram", "org.jctools", "org.nanohttpd", "com.grack")
      val filterShadedDependencies = new RuleTransformer(new RewriteRule {
        override def transform(n: Node): Seq[Node] = {
          if(n.label == "dependency") {
            val group = (n \ "groupId").text
            if (shadedGroups.find(eo => eo.equalsIgnoreCase(group)).nonEmpty) Seq.empty else n
          } else n
        }
      })

      filterShadedDependencies(originalPom)
    }}

  )

lazy val testkit = (project in file("kamon-testkit"))
  .settings(moduleName := "kamon-testkit")
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.0.1"
    )
  ).dependsOn(core)


lazy val coreTests = (project in file("kamon-core-tests"))
  .settings(
    moduleName := "kamon-core-tests",
    resolvers += Resolver.mavenLocal,
    fork in Test := true)
  .settings(noPublishing: _*)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.0.1" % "test",
      "ch.qos.logback" % "logback-classic" % "1.2.3" % "test"
    )
  ).dependsOn(testkit)


lazy val coreBench = (project in file("kamon-core-bench"))
  .enablePlugins(JmhPlugin)
  .settings(
    moduleName := "kamon-core-bench",
    fork in Test := true)
  .settings(noPublishing: _*)
  .settings(commonSettings: _*)
  .dependsOn(core)