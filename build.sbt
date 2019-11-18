/*
 *  ==========================================================================================
 *  Copyright © 2013-2019 The Kamon Project <https://kamon.io/>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 *  except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the
 *  License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied. See the License for the specific language governing permissions
 *  and limitations under the License.
 *  ==========================================================================================
 */

val kamonCore         = "io.kamon"            %%  "kamon-core"                      % "2.0.2"
val kamonTestkit      = "io.kamon"            %%  "kamon-testkit"                   % "2.0.2"
val kamonCommon       = "io.kamon"            %%  "kamon-instrumentation-common"    % "2.0.0"
val kanela            = "io.kamon"            %   "kanela-agent"                    % "1.0.3"
val mongoSyncDriver   = "org.mongodb"         %   "mongodb-driver-sync"             % "3.11.0"
val mongoScalaDriver  = "org.mongodb.scala"   %%  "mongo-scala-driver"              % "2.7.0"
val mongoDriver       = "org.mongodb"         %   "mongodb-driver-reactivestreams"  % "1.12.0"
val embeddedMongo     = "de.flapdoodle.embed" %   "de.flapdoodle.embed.mongo"       % "2.2.0"

lazy val root = Project("kamon-mongo", file("."))
  .settings(noPublishing: _*)
  .settings(crossScalaVersions := Nil)
  .aggregate(instrumentation)


lazy val instrumentation = Project("instrumentation", file("instrumentation"))
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    name := "kamon-mongo",
    bintrayPackage := "kamon-mongo",
    moduleName := "kamon-mongo",
    scalaVersion := "2.13.1",
    resolvers += Resolver.mavenLocal,
    kanelaAgentVersion := "1.0.3",
    crossScalaVersions := Seq("2.11.12", "2.12.8", "2.13.1"),
    libraryDependencies ++=
      compileScope(kamonCore, kamonCommon) ++
      providedScope(kanela, mongoDriver, mongoSyncDriver, mongoScalaDriver) ++
      testScope(kamonTestkit, embeddedMongo, scalatest) ++
      testScope(
        "io.kamon" %% "kamon-bundle" % "2.0.2",
        "io.kamon" %% "kamon-apm-reporter" % "2.0.0"
      )

  )
