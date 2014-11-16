/* =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

import sbt._
import Keys._

/** Sigar build specific settings. */
object Sigar {
  import UnzipTask._
  import sbt.Package._
  import Dependencies._

  /** Helper settings for extracted sigar sources. */
  lazy val sigarSources = SettingKey[File]("sigar-sources", "Location of extracted sigar sources.")

  /** Native o/s libraries folder inside kamon-sigar.jar. Hardcoded in [SigarAgent.java]. */
  lazy val nativeFolder = "native"

  /** Full class name of the sigar load time agent. Provides Agent-Class and Premain-Class contracts. */
  lazy val agentClass = "kamon.sigar.SigarAgent"

  /** A name filter which matches java source files. */
  lazy val sourceFilter: NameFilter = new PatternFilter(
    java.util.regex.Pattern.compile("""(.+\.java)""")
  )

  /** A name filter which matches java class files. */
  lazy val classFilter: NameFilter = new PatternFilter(
    java.util.regex.Pattern.compile("""(.+\.class)""")
  )

  /** A name filter which matches native o/s libraries. */
  lazy val nativeFilter: NameFilter = new PatternFilter(
    java.util.regex.Pattern.compile("""(.+\.dll)|(.+\.dylib)|(.+\.lib)|(.+\.sl)|(.+\.so)""")
  )

  /** Location of latest sigar artifacts. */
  lazy val redhatResolver = "RedHat/JBoss Repository" at "http://repository.jboss.org/nexus/content/groups/public-jboss"

  /** Sigar build specific settings. */
  lazy val settings = Seq(

    /** Pure java build.*/
    autoScalaLibrary := false,

    /** Use RedHat artifacts. */
    resolvers += redhatResolver,

    /** Ensure no transitive dependency. */
    libraryDependencies ++=
      provided(sigarJar, sigarZip) ++
      test(junit, junitInterface, slf4Api, logback),

    /** Location of sigar source extraction. */
    sigarSources := target.value / "sigar-sources",

    /** Origianl sigar resources extraction and relocation. */
    unzipTask := {
      val log = streams.value.log
      val report = update.value

      log.debug(s"Unpack SRC: ${sigarJar}")
      val srcTarget = sigarSources.value
      val srcArtifact = locateArtifact(report, sigarJar, "sources")
      val srcFileList = extractArtifact(srcArtifact, srcTarget, sourceFilter, false)

      log.debug(s"Unpack JAR: ${sigarJar}")
      val jarTarget = (classDirectory in Compile).value
      val jarArtifact = locateArtifact(report, sigarJar)
      val jarFileList = extractArtifact(jarArtifact, jarTarget, classFilter, false)

      log.debug(s"Unpack ZIP ${sigarZip}")
      val zipTarget = jarTarget / nativeFolder
      val zipArtifact = locateArtifact(report, sigarZip)
      val zipFileList = extractArtifact(zipArtifact, zipTarget, nativeFilter, true)
    },

    /** Unpack sigar resources before compile. */
    (Keys.compile in Compile) <<= (Keys.compile in Compile) dependsOn unzipTask,

    /** Include original sigar sources as our own. */
    (packageSrc in Compile) <<= (packageSrc in Compile) dependsOn unzipTask,
    (mappings in (Compile, packageSrc)) ++= {
      val base = sigarSources.value
      val finder = base ** sourceFilter
      val pairList = finder x relativeTo(base)
      pairList
    },

    /** Invoke verbose tesing in separate JVM. */
    testOptions += Tests.Argument(TestFrameworks.JUnit, "-v", "-a"),
    fork in Test := true,

    /** Ensure JVM agent packaging. */
    packageOptions in (Compile, packageBin) ++= Seq(
      ManifestAttributes("Agent-Class" -> agentClass),
      ManifestAttributes("Premain-Class" -> agentClass),
      ManifestAttributes("Kamon-Sigar-Version" -> sigarVersion)
    )

  )

}
