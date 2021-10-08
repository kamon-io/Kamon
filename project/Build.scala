import sbt.{Configuration, _}
import Keys._
import sbt.librarymanagement.{Configuration, Configurations}
import Configurations.Compile
import sbtassembly.AssemblyPlugin
import sbtassembly.AssemblyPlugin.autoImport.{MergeStrategy, assembleArtifact, assembly, assemblyExcludedJars, assemblyMergeStrategy, assemblyOption, assemblyPackageScala}
import java.util.Calendar

import Def.Initialize
import com.jsuereth.sbtpgp.PgpKeys.useGpgPinentry
import sbtrelease.ReleasePlugin.autoImport._
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport._
import sbtrelease.ReleaseStateTransformations._

import scala.sys.process._
import com.lightbend.sbt.javaagent.JavaAgent.JavaAgentKeys.javaAgents
import de.heikoseeberger.sbtheader.{HeaderPlugin, License}
import sbt.plugins.JvmPlugin
import sbtdynver.DynVerPlugin.autoImport.dynver
import xerial.sbt.Sonatype.SonatypeKeys._

object BaseProject extends AutoPlugin {

  object autoImport {

    /** Marker configuration for dependencies that will be shaded into their module's jar.  */
    lazy val Shaded = config("shaded").hide

    val kanelaAgent       = "io.kamon"              %  "kanela-agent"    % "1.0.12"
    val slf4jApi          = "org.slf4j"             %  "slf4j-api"       % "1.7.25"
    val slf4jnop          = "org.slf4j"             %  "slf4j-nop"       % "1.7.24"
    val logbackClassic    = "ch.qos.logback"        %  "logback-classic" % "1.2.3"
    val scalatest         = "org.scalatest"         %% "scalatest"       % "3.2.9"
    val hdrHistogram      = "org.hdrhistogram"      %  "HdrHistogram"    % "2.1.10"
    val okHttp            = "com.squareup.okhttp3"  %  "okhttp"          % "3.14.7"
    val okHttpMockServer  = "com.squareup.okhttp3"  %  "mockwebserver"   % "3.10.0"
    val jsqlparser        = "com.github.jsqlparser" % "jsqlparser"       % "4.1"
    val oshiCore          = "com.github.oshi"       %  "oshi-core"       % "5.7.5"


    val kanelaAgentVersion = settingKey[String]("Kanela Agent version")
    val kanelaAgentJar = taskKey[File]("Kanela Agent jar")

    val noPublishing = Seq(
      publish / skip := true,
      publishLocal := {},
      publishArtifact := false
    )

    val instrumentationSettings = Seq(
      kanelaAgentVersion := kanelaAgent.revision,
      javaAgents := Seq("io.kamon" % "kanela-agent" % kanelaAgentVersion.value % "runtime;test")
    )

    // This installs the GPG signing key from the
    setupGpg()

    def compileScope(deps: ModuleID*): Seq[ModuleID]  = deps map (_ % "compile")
    def testScope(deps: ModuleID*): Seq[ModuleID]     = deps map (_ % "test")
    def providedScope(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
    def optionalScope(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile,optional")

    /**
      * Joins all the sources from the base and extra configurations. If the same source exists in both configurations
      * then the one in the base configuration will remain.
      */
    def joinSources(base: Configuration, extra: Configuration): Initialize[Task[Seq[File]]] = Def.task {
      import Path.relativeTo

      val baseSources = (base / unmanagedSources).value.pair(relativeTo((base / unmanagedSourceDirectories).value))
      val extraSources = (extra / unmanagedSources).value.pair(relativeTo((extra / unmanagedSourceDirectories).value))
      val manSources = (extra / managedSources).value.pair(relativeTo((extra / managedSourceDirectories).value))

      val allSources = (
        baseSources.filterNot { case (_, path) => extraSources.exists(_._2 == path) } ++
        manSources ++
        extraSources
      )

      allSources.map(_._1)
    }

    /**
      * Joins all products found within the provided folders. If the same file exists in more than one folder then the
      * last occurrence wins.
      */
    def joinProducts(allProducts: Seq[File]*): Seq[(File, String)] = {
      val joinedProducts = scala.collection.mutable.Map.empty[String, File]
      allProducts.flatMap(_.flatMap(Path.allSubpaths)).foreach {
        case (file, name) => joinedProducts.put(name, file)
      }

      joinedProducts.map(_.swap).toSeq
    }
  }

  override def requires: Plugins = JvmPlugin && HeaderPlugin
  override def trigger: PluginTrigger = allRequirements
  override def projectSettings: Seq[_root_.sbt.Def.Setting[_]] =
    commonSettings ++ compilationSettings ++ releaseSettings ++ publishingSettings

  private lazy val commonSettings = Seq(
    // We are replacing the "date-time" part of the version generated by sbt-dynver so that we
    // get the same version value, even when re-applying settings during cross actions that
    // might take longer than a minute to complete.
    version ~= (_.replaceAll("[+]\\d{8}[-]\\d{4}", "-dirty")),
    dynver ~= (_.replaceAll("[+]\\d{8}[-]\\d{4}", "-dirty")),
    exportJars := true,
    Test / fork := true,
    Test / parallelExecution := false,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-F", "2.5"),
    startYear := Some(2013),
    organization := "io.kamon",
    organizationName := "The Kamon Project",
    headerLicense := licenseTemplate(startYear.value),
    autoImport.kanelaAgentJar := findKanelaAgentJar.value,
    organizationHomepage := Some(url("https://kamon.io/")),
    Global / concurrentRestrictions += Tags.limit(Tags.Test, 1),
    licenses += (("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))),
    resolvers += Resolver.mavenLocal,
    headerLicense := Some(HeaderLicense.ALv2("2013-2021","The Kamon Project <https://kamon.io>")),
    Keys.commands += Command.command("testUntilFailed") { state: State =>
      "test" :: "testUntilFailed" :: state
    }
  )

  private lazy val compilationSettings = Seq(
    crossPaths := true,
    scalaVersion := "2.12.11",
    crossScalaVersions := Seq("2.11.12", "2.12.11", "2.13.6"),
    javacOptions := Seq(
      "-source", "1.8",
      "-target", "1.8",
      "-Xlint:-options",
      "-encoding", "UTF-8",
      "-XDignore.symbol.file"
    ),
    scalacOptions := Seq(
      "-g:vars",
      "-feature",
      "-unchecked",
      "-deprecation",
      "-target:jvm-1.8",
      "-Ywarn-dead-code",
      "-encoding", "UTF-8",
      "-language:postfixOps",
      "-language:higherKinds",
      "-Xlog-reflective-calls",
      "-language:existentials",
      "-language:implicitConversions"
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2,11)) => Seq("-Xfuture", "-Ybackend:GenASM")
      case Some((2,12)) => Seq("-Xfuture", "-opt:l:method,-closure-invocations")
      case Some((2,13)) => Seq.empty
      case Some((3, _)) => Seq("-source:3.0-migration")
      case _ => Seq.empty
    })
  )

  private lazy val releaseSettings = Seq(
    releaseCrossBuild := false,
    releaseProcess := kamonReleaseProcess.value
  )

  private lazy val publishingSettings = Seq(
    publishTo := sonatypePublishToBundle.value,
    pomExtra := defaultPomExtra(),
    Test / publishArtifact := false,
    Global / useGpgPinentry := true,
    pomIncludeRepository := { _ => false }
  )

  private def licenseTemplate(startYear: Option[Int]) = {
    val fromYear = startYear.getOrElse(2013)
    val thisYear = Calendar.getInstance().get(Calendar.YEAR)

    Some(License.Custom(
      s"""
         | ==========================================================================================
         | Copyright © $fromYear-$thisYear The Kamon Project <https://kamon.io/>
         |
         | Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
         | except in compliance with the License. You may obtain a copy of the License at
         |
         |     http://www.apache.org/licenses/LICENSE-2.0
         |
         | Unless required by applicable law or agreed to in writing, software distributed under the
         | License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
         | either express or implied. See the License for the specific language governing permissions
         | and limitations under the License.
         | ==========================================================================================
      """.trim().stripMargin
    ))
  }

  def findKanelaAgentJar = Def.task {
    update.value.matching {
      moduleFilter(organization = "io.kamon", name = "kanela-agent") &&
        artifactFilter(`type` = "jar")
    }.head
  }

  private def defaultPomExtra() = {
    <url>http://kamon.io</url>
      <scm>
        <url>git://github.com/kamon-io/Kamon.git</url>
        <connection>scm:git:git@github.com:kamon-io/Kamon.git</connection>
      </scm>
      <developers>
        <developer><id>ivantopo</id><name>Ivan Topolnjak</name><url>https://twitter.com/ivantopo</url></developer>
        <developer><id>dpsoft</id><name>Diego Parra</name><url>https://twitter.com/diegolparra</url></developer>
      </developers>
  }

  private def setupGpg(): Unit = {
    sys.env.get("PGP_SECRET").foreach(secret => {
      (s"echo $secret" #| "base64 --decode" #| "gpg --import --no-tty --batch ").!
    })
  }

  private def kamonReleaseProcess = Def.setting {
    val publishStep =
      if(isSnapshot.value)
        releaseStepCommandAndRemaining("+publish")
      else
        releaseStepCommandAndRemaining("+publishSigned")

    Seq[ReleaseStep](
      runClean,
      publishStep,
      releaseStepCommandAndRemaining("sonatypeBundleRelease")
    )
  }
}

/**
  * These settings are required by all projects we have using the sbt-assembly plugin. These settings allow publishing
  * fat jars that do not reference their shaded dependencies in the pom.xml files without having to create separate code
  * and publishing projects as described in the sbt-assembly readme [1].
  *
  * [1]: https://github.com/sbt/sbt-assembly#q-despite-the-concerned-friends-i-still-want-publish-fat-jars-what-advice-do-you-have
  */
object AssemblyTweaks extends AutoPlugin {
  import BaseProject.autoImport.Shaded

  override def requires = AssemblyPlugin
  override def trigger = allRequirements
  override def projectConfigurations: Seq[Configuration] = Seq(Shaded)
  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    exportJars := true,
    assembly / test := {},
    Compile / packageBin := assembly.value,
    assembly / fullClasspath := (Shaded / externalDependencyClasspath).value ++ (Compile / products).value.classpath,
    assembly / assemblyOption := (assembly / assemblyOption).value.copy(
      includeBin = true,
      includeScala = false,
      includeDependency = true,
      cacheOutput = false
    ),
    assembly / assemblyMergeStrategy := {
      case s if s.startsWith("LICENSE") => MergeStrategy.discard
      case s if s.startsWith("about") => MergeStrategy.discard
      case x => (assembly / assemblyMergeStrategy).value(x)
    }
  ) ++ inConfig(Shaded)(Defaults.configSettings)
}
