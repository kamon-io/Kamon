/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon.instrumentation.tapir

import akka.http.scaladsl.server.Directives._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import sttp.tapir.{endpoint, path, plainBody}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object TestRoutes {

  // hello/{}
  private val hello = endpoint.get
    .in("hello").in(path[String])
    .out(plainBody[String])
  private val helloRoute = AkkaHttpServerInterpreter().toRoute(
    hello.serverLogic[Future] { name => Future.successful(Right(name)) }
  )

  // hello/{}/with/{}/mixed/{}/types
  private val nested = endpoint.get
    .in("nested").in(path[String]("integer_param"))
    .in("with").in(path[Int])
    .in("mixed").in(path[Boolean]("other_param"))
    .in("types").out(plainBody[String])
  private val nestedRoute = AkkaHttpServerInterpreter().toRoute(
    nested.serverLogic[Future] { x => Future.successful(Right[Unit, String](x.toString())) }
  )

  val routes = helloRoute ~ nestedRoute
}
