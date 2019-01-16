/* =========================================================================================
 * Copyright © 2013-2019 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS I
 * S" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

val kamonCore         = "io.kamon"                  %%  "kamon-core"            % "1.1.3"
val kamonTestkit      = "io.kamon"                  %%  "kamon-testkit"         % "1.1.3"
val sigarLoader       = "io.kamon"                  %   "sigar-loader"          % "1.6.5-rev003"
val logback           = "ch.qos.logback"            %   "logback-classic"       % "1.0.13"
val slf4jJul          = "org.slf4j"                 %   "jul-to-slf4j"          % "1.7.7"

name := "kamon-system-metrics"

libraryDependencies ++=
  compileScope(kamonCore, sigarLoader) ++
  testScope(scalatest, kamonTestkit, logback, slf4jJul)

resolvers += Resolver.bintrayRepo("kamon-io", "releases")
resolvers += Resolver.bintrayRepo("kamon-io", "snapshots")
