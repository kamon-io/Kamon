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
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream._
import akka.stream.scaladsl.{ BidiFlow, Flow }
import akka.stream.stage._
import kamon.Kamon
import kamon.akka.http.AkkaHttpExtension
import kamon.trace.Tracer
import kamon.util.logger.LazyLogger

/**
 * Wraps an {@code Flow[HttpRequest,HttpResponse]} with the necessary steps to output
 * the http metrics defined in AkkaHttpServerMetrics.
 * credits to @jypma.
 */
object FlowWrapper {

  val log = LazyLogger("FlowWrapper")
  val metrics = AkkaHttpExtension.metrics

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

          val defaultTraceName = AkkaHttpExtension.generateTraceName(request)

          val token = if (AkkaHttpExtension.settings.includeTraceTokenHeader) {
            request.headers.find(_.name.equalsIgnoreCase(AkkaHttpExtension.settings.traceTokenHeaderName)).map(_.value)
          } else None

          val newContext = Kamon.tracer.newContext(defaultTraceName, token)
          Tracer.setCurrentContext(newContext)

          metrics.recordRequest()
          push(requestOut, request)
        }
      })

      setHandler(requestOut, new OutHandler {
        override def onPull(): Unit = pull(requestIn)
      })

      setHandler(responseIn, new InHandler {
        override def onPush(): Unit = {
          val response = Tracer.currentContext.collect { ctx ⇒
            ctx.finish()

            val response = grab(responseIn)
            metrics.recordResponse(response, ctx.name)

            if (AkkaHttpExtension.settings.includeTraceTokenHeader)
              includeTraceToken(response, AkkaHttpExtension.settings.traceTokenHeaderName, ctx.token)
            else response

          } getOrElse grab(responseIn)

          push(responseOut, response)
        }
      })

      setHandler(responseOut, new OutHandler {
        override def onPull(): Unit = pull(responseIn)
      })

      override def preStart(): Unit = metrics.recordConnectionOpened()
      override def postStop(): Unit = metrics.recordConnectionClosed()
    }
  }

  def apply(flow: Flow[HttpRequest, HttpResponse, NotUsed]): Flow[HttpRequest, HttpResponse, NotUsed] = BidiFlow.fromGraph(wrap()).join(flow)

  private def includeTraceToken(response: HttpResponse, traceTokenHeaderName: String, token: String): HttpResponse = response match {
    case response: HttpResponse ⇒ response.withHeaders(response.headers ++ Seq(RawHeader(traceTokenHeaderName, token)))
    case other                  ⇒ other
  }
}

