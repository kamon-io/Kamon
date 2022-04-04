/*
 * =========================================================================================
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

package kamon.netty

import kamon.Kamon
import kamon.context.Context
import kamon.netty.Clients.withNioClient
import kamon.netty.Servers.withNioServer
import kamon.testkit.{MetricInspection, SpanInspection, TestSpanReporter}
import kamon.trace.Span
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar._
import org.scalatest.wordspec.AnyWordSpec

class NettyHTTPTracingSpec extends AnyWordSpec
  with Matchers
  with MetricInspection.Syntax
  with Eventually
  with SpanInspection
  with TestSpanReporter
  with  OptionValues {

  "The Netty HTTP span propagation" should {
    "propagate the span from the client to the server" in {
      withNioServer(9001) { port =>
        withNioClient(port) { httpClient =>
          val clientSpan =  Kamon.spanBuilder("test-span").start()
          Kamon.runWithContext(Context.of(Span.Key, clientSpan)) {
            val httpGet = httpClient.get(s"http://localhost:$port/route?param=123")
            httpClient.execute(httpGet)

            eventually(timeout(5 seconds)) {
              val serverSpan = testSpanReporter().nextSpan().value
              val clientSpan = testSpanReporter.nextSpan().value

              serverSpan.operationName shouldBe "route.get"
              serverSpan.kind shouldBe Span.Kind.Server

              clientSpan.operationName shouldBe "/route"
              clientSpan.kind shouldBe Span.Kind.Client
            }
          }
        }
      }
    }

    "contain a span error when an internal server error(500) occurs" in {
      withNioServer(9002) { port =>
        withNioClient(port) { httpClient =>
          val clientSpan =  Kamon.spanBuilder("test-span-with-error").start()
          Kamon.runWithContext(Context.of(Span.Key, clientSpan)) {
            val httpGet = httpClient.get(s"http://localhost:$port/error")
            httpClient.execute(httpGet)


            eventually(timeout(5 seconds)) {
              val serverSpan = testSpanReporter().nextSpan().value
              val clientSpan = testSpanReporter.nextSpan().value

              serverSpan.operationName shouldBe "error.get"
              serverSpan.kind shouldBe Span.Kind.Server

              clientSpan.operationName shouldBe "/error"
              clientSpan.kind shouldBe Span.Kind.Client
            }
          }
        }
      }
    }

    "propagate the span from the client to the server with chunk-encoded request" in {
      withNioServer(9003) { port =>
        withNioClient(port) { httpClient =>
          val clientSpan = Kamon.spanBuilder("client-chunk-span").start()
          Kamon.runWithContext(Context.of(Span.Key, clientSpan)) {
            val (httpPost, chunks) = httpClient.postWithChunks(s"http://localhost:$port/fetch-in-chunks-request", "test 1", "test 2")
            httpClient.executeWithContent(httpPost, chunks)

            eventually(timeout(5 seconds)) {
              val serverSpan = testSpanReporter().nextSpan().value
              val clientSpan = testSpanReporter.nextSpan().value

              serverSpan.operationName shouldBe "fetch-in-chunks-request.post"
              serverSpan.kind shouldBe Span.Kind.Server

              clientSpan.operationName shouldBe s"/fetch-in-chunks-request"
              clientSpan.kind shouldBe Span.Kind.Client
            }
          }
        }
      }
    }

    "propagate the span from the client to the server with chunk-encoded response" in {
      withNioServer(9004) { port =>
        withNioClient(port) { httpClient =>
          val clientSpan = Kamon.spanBuilder("client-chunk-span").start()
          Kamon.runWithContext(Context.of(Span.Key, clientSpan)) {
            val (httpPost, chunks) = httpClient.postWithChunks(s"http://localhost:$port/fetch-in-chunks-response", "test 1", "test 2")
            httpClient.executeWithContent(httpPost, chunks)

            eventually(timeout(5 seconds)) {
              val serverSpan = testSpanReporter().nextSpan().value
              val clientSpan = testSpanReporter.nextSpan().value

              serverSpan.operationName shouldBe "fetch-in-chunks-response.post"
              serverSpan.kind shouldBe Span.Kind.Server

              clientSpan.operationName shouldBe s"/fetch-in-chunks-response"
              clientSpan.kind shouldBe Span.Kind.Client
            }
          }
        }
      }
    }

    "create a new span when it's coming a request without one" in {
      withNioServer(9005) { port =>
        withNioClient(port) { httpClient =>
          val httpGet = httpClient.get(s"http://localhost:$port/route?param=123")
          httpClient.execute(httpGet)

          eventually(timeout(5 seconds)) {
            val serverSpan = testSpanReporter().nextSpan().value

            serverSpan.operationName shouldBe "route.get"
            serverSpan.kind shouldBe Span.Kind.Server
          }
        }
      }
    }

    "create a new span for each request" in {
      withNioServer(9006) { port =>
        withNioClient(port) { httpClient =>
          val clientSpan =  Kamon.spanBuilder("test-span").start()
          Kamon.runWithContext(Context.of(Span.Key, clientSpan)) {
            httpClient.execute(httpClient.get(s"http://localhost:$port/route?param=123"))
            httpClient.execute(httpClient.get(s"http://localhost:$port/route?param=123"))

            eventually(timeout(5 seconds)) {
              val serverSpan = testSpanReporter().nextSpan().value
              val clientSpan = testSpanReporter.nextSpan().value

              serverSpan.operationName shouldBe "route.get"
              serverSpan.kind shouldBe Span.Kind.Server

              clientSpan.operationName shouldBe "/route"
              clientSpan.kind shouldBe Span.Kind.Client

              serverSpan.trace.id shouldBe clientSpan.trace.id

            }
          }
        }
      }
    }
  }
}
