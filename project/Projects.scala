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
import sbtdoge._

object Projects extends Build {
  import AspectJ._
  import Settings._
  import Dependencies._

  lazy val kamon = Project("kamon", file("."))
    .enablePlugins(CrossPerProjectPlugin)
    .aggregate(kamonCore, kamonScala, kamonAkka, kamonSpray, kamonNewrelic, kamonPlayground, kamonTestkit,
      kamonStatsD, kamonRiemann, kamonDatadog, kamonSPM, kamonSystemMetrics, kamonLogReporter, kamonAkkaRemote, kamonJdbc, kamonElasticsearch,
      kamonAnnotation, kamonPlay23, kamonPlay24, kamonPlay25, kamonJMXReporter, kamonFluentd, kamonKhronus,
      kamonAutoweave, kamonInfluxDB)
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
        compile(akkaActor, hdrHistogram, typesafeConfig, slf4jApi) ++
        provided(aspectJ) ++
        optional(logback) ++
        test(scalatest, akkaTestKit, akkaSlf4j, slf4jJul, slf4jLog4j, logback))


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
          test(scalatest, akkaTestKit, akkaSlf4j, slf4jJul, slf4jLog4j, logback))


  lazy val kamonScala = Project("kamon-scala", file("kamon-scala"))
    .dependsOn(kamonCore % "compile->compile;test->test")
    .settings(basicSettings: _* )
    .settings(formatSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(
      libraryDependencies ++=
        compile() ++
        provided(aspectJ) ++
        optional(scalazConcurrent, twitterUtilCore) ++
        test(scalatest, akkaTestKit, akkaSlf4j, slf4jJul, slf4jLog4j, logback))

  lazy val kamonAkkaRemote = Project("kamon-akka-remote", file("kamon-akka-remote"))
    .dependsOn(kamonAkka)
    .settings(basicSettings: _* )
    .settings(formatSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(akkaRemote, akkaCluster) ++
        provided(aspectJ) ++
        test(scalatest, akkaTestKit, akkaSlf4j, slf4jJul, slf4jLog4j, logback))


  lazy val kamonSpray = Project("kamon-spray", file("kamon-spray"))
    .dependsOn(kamonCore % "compile->compile;test->test", kamonAkka, kamonTestkit % "test")
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(akkaActor, sprayCan, sprayClient, sprayRouting) ++
        provided(aspectJ) ++
        test(scalatest, akkaTestKit, sprayTestkit, akkaSlf4j, slf4jJul, slf4jLog4j, logback))

  lazy val kamonNewrelic = Project("kamon-newrelic", file("kamon-newrelic"))
    .dependsOn(kamonCore % "compile->compile;test->test", kamonAkka, kamonTestkit % "test->test")
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(sprayCan, sprayClient, sprayRouting, sprayJson, sprayJsonLenses, newrelic, akkaSlf4j) ++
        provided(aspectJ, newrelic) ++
        test(scalatest, akkaTestKit, sprayTestkit, slf4jApi, akkaSlf4j))


  lazy val kamonPlayground = Project("kamon-playground", file("kamon-playground"))
    .dependsOn(kamonSpray, kamonNewrelic, kamonStatsD, kamonDatadog, kamonLogReporter, kamonSystemMetrics,
      kamonJMXReporter,kamonAutoweave,kamonJdbc)
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
        test(slf4jApi, slf4jnop))

  lazy val kamonPlay23 = Project("kamon-play-23", file("kamon-play-2.3.x"))
    .dependsOn(kamonCore % "compile->compile;test->test", kamonScala)
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(play23, playWS23) ++
        provided(aspectJ) ++
        test(playTest23, akkaTestKit, slf4jApi))

  lazy val kamonPlay24 = Project("kamon-play-24", file("kamon-play-2.4.x"))
    .dependsOn(kamonCore % "compile->compile;test->test", kamonScala)
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(play24, playWS24) ++
        provided(aspectJ, typesafeConfig) ++
        test(playTest24, akkaTestKit, slf4jApi))

  lazy val kamonPlay25 = Project("kamon-play-25", file("kamon-play-2.5.x"))
    .dependsOn(kamonCore % "compile->compile;test->test", kamonScala)
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(
      crossScalaVersions := Seq(ScalaVersion),
      libraryDependencies ++=
        compile(play25, playWS25) ++
        provided(aspectJ, typesafeConfig) ++
        test(playTest25, akkaTestKit, slf4jApi))

  lazy val kamonInfluxDB = Project("kamon-influxdb", file("kamon-influxdb"))
    .dependsOn(kamonCore % "compile->compile;test->test")
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(sprayCan, sprayClient, sprayRouting, akkaSlf4j, akkaActor, typesafeConfig) ++
        test(scalatest, sprayCan, sprayClient, akkaTestKit, slf4jApi, slf4jnop, typesafeConfig))

  lazy val kamonStatsD = Project("kamon-statsd", file("kamon-statsd"))
    .dependsOn(kamonCore % "compile->compile;test->test")
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(akkaActor) ++
        test(scalatest, akkaTestKit, slf4jApi, slf4jnop))

  lazy val kamonRiemann = Project("kamon-riemann", file("kamon-riemann"))
    .dependsOn(kamonCore % "compile->compile;test->test")
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(akkaActor) ++ compile(riemannClient) ++
          test(scalatest, akkaTestKit, slf4jApi, slf4jnop))

  lazy val kamonDatadog = Project("kamon-datadog", file("kamon-datadog"))
    .dependsOn(kamonCore % "compile->compile;test->test")
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(akkaActor) ++
        test(scalatest, akkaTestKit, slf4jApi, slf4jnop))


  lazy val kamonLogReporter = Project("kamon-log-reporter", file("kamon-log-reporter"))
    .dependsOn(kamonCore)
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(akkaActor) ++
        test(scalatest, akkaTestKit, slf4jApi, slf4jnop))


  lazy val kamonSystemMetrics = Project("kamon-system-metrics", file("kamon-system-metrics"))
    .dependsOn(kamonCore % "compile->compile;test->test")
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(fork in Test :=  true)
    .settings(
      libraryDependencies ++=
        compile(sigarLoader) ++
        test(scalatest, akkaTestKit, slf4jApi, slf4jJul, slf4jLog4j, logback))

  lazy val kamonJdbc = Project("kamon-jdbc", file("kamon-jdbc"))
    .dependsOn(kamonCore % "compile->compile;test->test")
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(
      libraryDependencies ++=
        test(h2,scalatest, akkaTestKit, slf4jApi) ++
        provided(aspectJ))

  lazy val kamonAutoweave = Project("kamon-autoweave", file("kamon-autoweave"))
    .dependsOn(kamonCore % "compile->compile;test->test")
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(
      libraryDependencies ++=
        test(scalatest, slf4jApi) ++
        compile(aspectJ))

  lazy val kamonElasticsearch = Project("kamon-elasticsearch", file("kamon-elasticsearch"))
    .dependsOn(kamonCore % "compile->compile;test->test")
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(elasticsearch) ++
        test(scalatest, akkaTestKit, slf4jApi) ++
        provided(aspectJ))

  lazy val kamonAnnotation = Project("kamon-annotation", file("kamon-annotation"))
    .dependsOn(kamonCore % "compile->compile;test->test")
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(aspectJSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(el) ++
          test(scalatest, akkaTestKit, slf4jApi) ++
          provided(aspectJ))

  lazy val kamonSPM = Project("kamon-spm", file("kamon-spm"))
    .dependsOn(kamonCore % "compile->compile;test->test")
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(sprayCan, sprayClient, sprayRouting, sprayJson, sprayJsonLenses, akkaSlf4j, libThrift) ++
        test(scalatest, akkaTestKit, slf4jApi, slf4jnop))

  lazy val kamonJMXReporter = Project("kamon-jmx", file("kamon-jmx"))
    .dependsOn(kamonCore)
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(akkaActor) ++
          test(scalatest, akkaTestKit, slf4jApi, slf4jnop))

  lazy val kamonFluentd = Project("kamon-fluentd", file("kamon-fluentd"))
    .dependsOn(kamonCore % "compile->compile;test->test")
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(akkaActor) ++ compile(fluentdLogger) ++
          test(scalatest, akkaTestKit, easyMock, slf4jApi, slf4jnop))

  lazy val kamonKhronus = Project("kamon-khronus", file("kamon-khronus"))
    .dependsOn(kamonCore % "compile->compile;test->test")
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(
      libraryDependencies ++=
        compile(khronusClient) ++
          test(scalatest, easyMock))

  val noPublishing = Seq(publish := (), publishLocal := (), publishArtifact := false)
}
