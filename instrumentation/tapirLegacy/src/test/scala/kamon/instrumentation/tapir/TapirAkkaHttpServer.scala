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

import akka.actor.ActorSystem
import akka.http.scaladsl.Http

import scala.concurrent.Future
import scala.concurrent.duration._

object TapirAkkaHttpServer {
  implicit val actorSystem: ActorSystem = ActorSystem()

  import actorSystem.dispatcher

  private var server: Future[Http.ServerBinding] = _

  def start = {
    server = Http().newServerAt("localhost", 8080).bindFlow(TestRoutes.routes)
  }

  def stop = server.flatMap(x => x.terminate(5.seconds))

}
