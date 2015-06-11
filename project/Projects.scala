/* =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

object Projects extends Build {
  import AspectJ._
  import Settings._
  import Dependencies._

  lazy val kamon = Project("kamon", file("."))
    .aggregate(kamonCore, kamonScala, kamonAkka, kamonSpray, kamonNewrelic, kamonPlayground, kamonTestkit,
      kamonStatsD, kamonDatadog, kamonSystemMetrics, kamonLogReporter, kamonAkkaRemote, kamonJdbc, kamonAnnotation, kamonPlay24)
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(noPublishing: _*)


  lazy val kamonCore: Project = Project("kamon-core", file("kamon-core"))
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(
      javacOptions in Compile ++= Seq("-XDignore.symbol.file"),
      libraryDependencies ++=
        compile(akkaActor, hdrHistogram, typesafeConfig) ++
        provided(aspectJ) ++
        optional(logback) ++
        test(scalatest, akkaTestKit, akkaSlf4j, slf4Jul, slf4Log4j, logback))


  lazy val kamonAkka = Project("kamon-akka", file("kamon-akka"))
    .dependsOn(kamonCore % "compile->compile;test->test")
    .dependsOn(kamonScala)
    .settings(basicSettings: _* )
    .settings(formatSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(akkaActor) ++
          provided(aspectJ) ++
          optional(logback) ++
          test(scalatest, akkaTestKit, akkaSlf4j, slf4Jul, slf4Log4j, logback))


  lazy val kamonScala = Project("kamon-scala", file("kamon-scala"))
    .dependsOn(kamonCore % "compile->compile;test->test")
    .settings(basicSettings: _* )
    .settings(formatSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(
      libraryDependencies ++=
        compile() ++
        provided(aspectJ) ++
        optional(scalazConcurrent) ++
        test(scalatest, akkaTestKit, akkaSlf4j, slf4Jul, slf4Log4j, logback))

  lazy val kamonAkkaRemote = Project("kamon-akka-remote", file("kamon-akka-remote"))
    .dependsOn(kamonAkka)
    .settings(basicSettings: _* )
    .settings(formatSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(akkaRemote, akkaCluster) ++
        provided(aspectJ) ++
        test(scalatest, akkaTestKit, akkaSlf4j, slf4Jul, slf4Log4j, logback))


  lazy val kamonSpray = Project("kamon-spray", file("kamon-spray"))
    .dependsOn(kamonCore % "compile->compile;test->test", kamonAkka, kamonTestkit % "test")
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(akkaActor, sprayCan, sprayClient, sprayRouting) ++
        provided(aspectJ) ++
        test(scalatest, akkaTestKit, sprayTestkit, akkaSlf4j, slf4Jul, slf4Log4j, logback))

  lazy val kamonNewrelic = Project("kamon-newrelic", file("kamon-newrelic"))
    .dependsOn(kamonCore % "compile->compile;test->test", kamonTestkit % "test->test")
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(sprayCan, sprayClient, sprayRouting, sprayJson, sprayJsonLenses, newrelic, akkaSlf4j) ++
        provided(aspectJ) ++
        test(scalatest, akkaTestKit, sprayTestkit, slf4Api, akkaSlf4j))


  lazy val kamonPlayground = Project("kamon-playground", file("kamon-playground"))
    .dependsOn(kamonSpray, kamonNewrelic, kamonStatsD, kamonDatadog, kamonLogReporter, kamonSystemMetrics)
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(noPublishing: _*)
    .settings(aspectJSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(akkaActor, akkaSlf4j, sprayCan, sprayClient, sprayRouting, logback))


  lazy val kamonTestkit = Project("kamon-testkit", file("kamon-testkit"))
    .dependsOn(kamonCore)
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(akkaActor, akkaTestKit) ++
        provided(aspectJ) ++
        test(slf4Api, slf4nop))

//  lazy val kamonPlay = Project("kamon-play", file("kamon-play"))
//    .dependsOn(kamonCore % "compile->compile;test->test", kamonScala)
//    .settings(basicSettings: _*)
//    .settings(formatSettings: _*)
//    .settings(aspectJSettings: _*)
//    .settings(
//      libraryDependencies ++=
//        compile(play, playWS) ++
//        provided(aspectJ) ++
//        test(playTest, akkaTestKit, slf4Api))

  lazy val kamonPlay24 = Project("kamon-play24", file("kamon-play24"))
    .dependsOn(kamonCore % "compile->compile;test->test", kamonScala)
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(play24, playWS24, typesafeConfig) ++
        provided(aspectJ) ++
        test(playTest24, akkaTestKit, slf4Api))

  lazy val kamonStatsD = Project("kamon-statsd", file("kamon-statsd"))
    .dependsOn(kamonCore % "compile->compile;test->test")
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(akkaActor) ++
        test(scalatest, akkaTestKit, slf4Api, slf4nop))

  lazy val kamonDatadog = Project("kamon-datadog", file("kamon-datadog"))
    .dependsOn(kamonCore % "compile->compile;test->test")
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(akkaActor) ++
        test(scalatest, akkaTestKit, slf4Api, slf4nop))


  lazy val kamonLogReporter = Project("kamon-log-reporter", file("kamon-log-reporter"))
    .dependsOn(kamonCore)
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(akkaActor) ++
        test(scalatest, akkaTestKit, slf4Api, slf4nop))


  lazy val kamonSystemMetrics = Project("kamon-system-metrics", file("kamon-system-metrics"))
    .dependsOn(kamonCore % "compile->compile;test->test")
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(fork in Test :=  true)
    .settings(
      libraryDependencies ++=
        compile(sigarLoader) ++
        test(scalatest, akkaTestKit, slf4Api, slf4Jul, slf4Log4j, logback))

  lazy val kamonJdbc = Project("kamon-jdbc", file("kamon-jdbc"))
    .dependsOn(kamonCore % "compile->compile;test->test")
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(
      libraryDependencies ++=
        test(h2,scalatest, akkaTestKit, slf4Api) ++
        provided(aspectJ))

  lazy val kamonAnnotation = Project("kamon-annotation", file("kamon-annotation"))
    .dependsOn(kamonCore % "compile->compile;test->test")
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(el) ++
          test(scalatest, akkaTestKit, slf4Api) ++
          provided(aspectJ))

  val noPublishing = Seq(publish := (), publishLocal := (), publishArtifact := false)
}
