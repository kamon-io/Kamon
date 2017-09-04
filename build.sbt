/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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


val kamonCore         = "io.kamon"                  %%  "kamon-core"            % "1.0.0-RC1-71a9f6519263f9d237b4ad189243f602d484cc92"
val sigarLoader       = "io.kamon"                  %   "sigar-loader"          % "1.6.6"
val logback           = "ch.qos.logback"            %   "logback-classic"       % "1.0.13"
val slf4jJul          = "org.slf4j"                 %   "jul-to-slf4j"          % "1.7.7"

name := "kamon-system-metrics"

libraryDependencies ++=
  compileScope(kamonCore, sigarLoader) ++
  testScope(scalatest, akkaDependency("testkit").value, akkaDependency("slf4j").value, logback, slf4jJul)

resolvers += Resolver.bintrayRepo("kamon-io", "releases")
