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

val kamonCore         = "io.kamon"                  %%  "kamon-core"            % "0.6.6"
val asyncHttpClient   = "org.asynchttpclient"       %   "async-http-client"     % "2.0.24"

name := "kamon-influxdb"

parallelExecution in Test in Global := false

libraryDependencies ++=
    compileScope(kamonCore, akkaDependency("slf4j").value, asyncHttpClient) ++
    testScope(scalatest, akkaDependency("testkit").value, slf4jApi, slf4jnop)

resolvers += Resolver.bintrayRepo("kamon-io", "releases")
