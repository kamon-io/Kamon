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

val kamonCore        = "io.kamon"             %% "kamon-core"    % "2.0.0-RC1"
val kamonTestkit     = "io.kamon"             %% "kamon-testkit" % "2.0.0-RC1"
val okHttp           = "com.squareup.okhttp3" %  "okhttp"        % "3.9.1"
val okHttpMockServer = "com.squareup.okhttp3" %  "mockwebserver" % "3.9.1"
val scalatest2_13    = "org.scalatest"        %% "scalatest"     % "3.0.8"

name := "kamon-influxdb"

libraryDependencies ++=
  compileScope(kamonCore, okHttp) ++
  testScope(scalatest2_13, kamonTestkit, okHttpMockServer, slf4jApi, slf4jnop)

crossScalaVersions := Seq("2.11.12", "2.12.7", "2.13.0")


resolvers += Resolver.bintrayRepo("kamon-io", "releases")
