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

package kamon.instrumentation.akka.grpc

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.Http
import kamon.tag.Lookups.plain
import kamon.testkit.{InitAndStopKamonAfterAll, TestSpanReporter}
import org.scalatest.OptionValues
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

class AkkaGrpcTracingSpec extends AnyWordSpec with InitAndStopKamonAfterAll with Matchers with Eventually
    with TestSpanReporter with OptionValues {

  implicit val system: ActorSystem = ActorSystem("akka-grpc-instrumentation")
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  val greeterService = GreeterServiceHandler(new GreeterServiceImpl())
  val serverBinding = Http()
    .newServerAt("127.0.0.1", 8598)
    .bind(greeterService)


  val client = GreeterServiceClient(GrpcClientSettings.connectToServiceAt("127.0.0.1", 8598).withTls(false))

  "the Akka gRPC instrumentation" should {
    "create spans for the server-side" in {
      client.sayHello(HelloRequest("kamon"))

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "helloworld.GreeterService/SayHello"
        span.metricTags.get(plain("component")) shouldBe "akka.grpc.server"
        span.metricTags.get(plain("rpc.system")) shouldBe "grpc"
        span.metricTags.get(plain("rpc.service")) shouldBe "helloworld.GreeterService"
        span.metricTags.get(plain("rpc.method")) shouldBe "SayHello"
      }
    }
  }
}
