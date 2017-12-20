/*
 * =========================================================================================
 * Copyright © 2013-2016 the kamon project <http://kamon.io/>
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

package kamon.akka.http.instrumentation

import akka.NotUsed
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream._
import akka.stream.scaladsl.{BidiFlow, Flow}
import akka.stream.stage._
import kamon.Kamon
import kamon.context.{Context => KamonContext}
import kamon.akka.http.AkkaHttpServerMetrics
import kamon.context.TextMap
import kamon.trace.{Span, SpanCodec}

/**
  * Wraps an {@code Flow[HttpRequest,HttpResponse]} with the necessary steps to output
  * the http metrics defined in AkkaHttpServerMetrics.
  * credits to @jypma.
  */
object FlowWrapper {
  import AkkaHttpServerMetrics._

  private def componentPrefixed(metricName: String) = s"akka.http.server.$metricName"

  def wrap() = new GraphStage[BidiShape[HttpRequest, HttpRequest, HttpResponse, HttpResponse]] {

    val requestIn = Inlet.create[HttpRequest]("request.in")
    val requestOut = Outlet.create[HttpRequest]("request.out")
    val responseIn = Inlet.create[HttpResponse]("response.in")
    val responseOut = Outlet.create[HttpResponse]("response.out")

    override val shape = BidiShape(requestIn, requestOut, responseIn, responseOut)

    override def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) {

      setHandler(requestIn, new InHandler {
        override def onPush(): Unit = {
          val request = grab(requestIn)

          val parentContext = extractContext(request)

          val span = Kamon.buildSpan(generateTraceName(request))
            .asChildOf(parentContext.get(Span.ContextKey))
            .withOperationName(request.uri.path.toString())
            .withMetricTag("span.kind", "server")
            .withTag(componentPrefixed("method"), request.method.value)
            .withTag(componentPrefixed("url"), request.uri.toString())
            .start()

          requestActive.increment()

          Kamon.storeContext(parentContext.withKey(Span.ContextKey, span))
          push(requestOut, request)
        }
        override def onUpstreamFinish(): Unit = complete(requestOut)
      })

      setHandler(requestOut, new OutHandler {
        override def onPull(): Unit = pull(requestIn)
        override def onDownstreamFinish(): Unit = cancel(requestIn)
      })

      setHandler(responseIn, new InHandler {
        override def onPush(): Unit = {
          val response = grab(responseIn)
          val span = Kamon.currentContext().get(Span.ContextKey)

          if(response.status.isFailure()) {
            val errorCode = response.status.value
            if (errorCode.startsWith("4")) span.setOperationName("not-found")
            span.addError(response.status.reason())
          }

          requestActive.decrement()

          span.finish()
          push(responseOut, includeTraceToken(response, Kamon.currentContext()))
        }
        override def onUpstreamFinish(): Unit = completeStage()
      })

      setHandler(responseOut, new OutHandler {
        override def onPull(): Unit = pull(responseIn)
        override def onDownstreamFinish(): Unit = cancel(responseIn)
      })

      override def preStart(): Unit = connectionOpen.increment()
      override def postStop(): Unit = connectionOpen.decrement()
    }
  }

  def apply(flow: Flow[HttpRequest, HttpResponse, NotUsed]): Flow[HttpRequest, HttpResponse, NotUsed] = BidiFlow.fromGraph(wrap()).join(flow)

  private def includeTraceToken(response: HttpResponse, context: KamonContext): HttpResponse = response match {
    case response: HttpResponse ⇒ response.withHeaders(
      response.headers ++ Kamon.contextCodec().HttpHeaders.encode(context).values.map(k => RawHeader(k._1, k._2))
    )
    case other                  ⇒ other
  }

  private def extractContext(request: HttpRequest) = Kamon.contextCodec().HttpHeaders.decode(new TextMap {
    private def headersKeyValueMap = request.headers.map(h => h.name -> h.value()).toMap
    override def values: Iterator[(String, String)] = headersKeyValueMap.iterator
    override def put(key: String, value: String): Unit = headersKeyValueMap + (key -> value)
    override def get(key: String): Option[String] = headersKeyValueMap.get(key)
  })
}

