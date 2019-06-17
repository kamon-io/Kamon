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
  .aggregate(core, corePublishing, statusPage, statusPagePublishing, testkit, tests, benchmarks)


lazy val core = (project in file("kamon-core"))
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(AssemblyPlugin)
  .settings(commonSettings: _*)
  .settings(noPublishing: _*)
  .settings(
    moduleName := "kamon-core",
    buildInfoKeys := Seq[BuildInfoKey](version),
    buildInfoPackage := "kamon.status",
    packageBin in Compile := assembly.value,
    assemblyExcludedJars in assembly := filterOut((fullClasspath in assembly).value, "slf4j-api", "config"),
    assemblyShadeRules in assembly := Seq(
      ShadeRule.rename("org.HdrHistogram.**"    -> "kamon.lib.@0").inAll,
      ShadeRule.rename("org.jctools.**"         -> "kamon.lib.@0").inAll,
      ShadeRule.keep(
        "kamon.Kamon",
        "kamon.context.**",
        "kamon.metric.**",
        "kamon.module.**",
        "kamon.status.**",
        "kamon.tag.**",
        "kamon.trace.**",
        "kamon.util.**",
        "org.HdrHistogram.AtomicHistogram",
        "org.jctools.queues.MpscArrayQueue",
      ).inAll
    ),
    libraryDependencies ++= Seq(
      "com.typesafe"      %  "config"       % "1.3.1",
      "org.slf4j"         %  "slf4j-api"    % "1.7.25",
      "org.hdrhistogram"  %  "HdrHistogram" % "2.1.9",
      "org.jctools"       %  "jctools-core" % "2.1.1",
    )
  )


lazy val corePublishing = project
  .settings(
    moduleName := "kamon-core",
    exportedProducts in Compile := (exportedProducts in (core, Compile)).value,
    packageBin in Compile := (packageBin in (core, Compile)).value,
    packageSrc in Compile := (packageSrc in (core, Compile)).value,
    packageDoc in Compile := (packageDoc in (core, Compile)).value,
    libraryDependencies ++= Seq(
      "com.typesafe"            %  "config"              % "1.3.1",
      "org.slf4j"               %  "slf4j-api"           % "1.7.25"
    )
  )


lazy val statusPage = (project in file("kamon-status-page"))
  .enablePlugins(AssemblyPlugin)
  .settings(commonSettings: _*)
  .settings(noPublishing: _*)
  .settings(
    packageBin in Compile := assembly.value,
    assemblyExcludedJars in assembly := filterOut((fullClasspath in assembly).value, "slf4j-api", "config", "kamon-core"),
    assemblyShadeRules in assembly := Seq(
      ShadeRule.rename("com.grack.nanojson.**"  -> "kamon.lib.@0").inAll,
      ShadeRule.rename("fi.iki.elonen.**"       -> "kamon.lib.@0").inAll,
    ),
    libraryDependencies ++= Seq(
      "org.nanohttpd" %  "nanohttpd" % "2.3.1",
      "com.grack"     %  "nanojson"  % "1.1"
    )
  ).dependsOn(core % "provided")


lazy val statusPagePublishing = project
  .settings(
    moduleName := "kamon-status-page",
    packageBin in Compile := (packageBin in (statusPage, Compile)).value,
    packageSrc in Compile := (packageSrc in (statusPage, Compile)).value,
    packageDoc in Compile := (packageDoc in (statusPage, Compile)).value,
  ).dependsOn(corePublishing)


lazy val testkit = (project in file("kamon-testkit"))
  .settings(commonSettings: _*)
  .settings(
    moduleName := "kamon-testkit",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.0.8" % "test"
    )
  ).dependsOn(core)


lazy val tests = (project in file("kamon-core-tests"))
  .settings(noPublishing: _*)
  .settings(commonSettings: _*)
  .settings(
    moduleName := "kamon-core-tests",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest"        % "3.0.8" % "test",
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
  javacOptions += "-XDignore.symbol.file",
  fork in Test := true,
  resolvers += Resolver.mavenLocal,
  concurrentRestrictions in Global += Tags.limit(Tags.Test, 1),
  scalaVersion := "2.13.0",
  crossScalaVersions := Seq("2.11.12", "2.12.7", "2.13.0"),
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-language:implicitConversions", "-language:higherKinds", "-language:existentials", "-language:postfixOps",
    "-unchecked"
  ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2,11)) => Seq("-Xfuture", "-Ybackend:GenASM","-Ydelambdafy:method","-target:jvm-1.8")
    case Some((2,12)) => Seq("-Xfuture", "-opt:l:method")
    case Some((2,13)) => Seq.empty
    case _ => Seq.empty
  }),
  assembleArtifact in assemblyPackageScala := false,
  assemblyMergeStrategy in assembly := {
    case s if s.startsWith("LICENSE") => MergeStrategy.discard
    case s if s.startsWith("about") => MergeStrategy.discard
    case x => (assemblyMergeStrategy in assembly).value(x)
  }
)

def filterOut(classPath: Classpath, patterns: String*): Classpath = {
  classPath filter { file => patterns.exists(file.data.getPath.contains(_))}
}
