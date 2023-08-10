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

package kamon.instrumentation.pekko.http

import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

import org.apache.pekko.NotUsed
import org.apache.pekko.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse}
import org.apache.pekko.stream.scaladsl.{BidiFlow, Flow, Keep}
import org.apache.pekko.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import org.apache.pekko.stream.{Attributes, BidiShape, Inlet, Outlet}
import org.apache.pekko.util.ByteString
import kamon.Kamon
import kamon.instrumentation.http.HttpServerInstrumentation.RequestHandler
import kamon.instrumentation.http.HttpServerInstrumentation
import kamon.util.CallingThreadExecutionContext
import kamon.instrumentation.pekko.http.PekkoHttpInstrumentation.{toRequest, toResponseBuilder}

import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.util.{Failure, Success}

/**
  * Wraps a {@code Flow[HttpRequest,HttpResponse]} with the necessary infrastructure to provide HTTP Server metrics,
  * tracing and Context propagation.
  *
  * Credits to @jypma.
  */
object ServerFlowWrapper {

  // Since we reuse the same instrumentation to track Play, we are allowing for the component tag to be changed
  // temporarily.
  private val _serverInstrumentations = TrieMap.empty[Int, HttpServerInstrumentation]
  private val _defaultOperationNames = TrieMap.empty[Int, String]
  private val _defaultSettings = Settings("pekko.http.server", "kamon.instrumentation.pekko.http.server")
  @volatile private var _wrapperSettings = _defaultSettings

  def apply(flow: Flow[HttpRequest, HttpResponse, NotUsed], interface: String, port: Int): Flow[HttpRequest, HttpResponse, NotUsed] =
    BidiFlow.fromGraph(wrapStage(_wrapperSettings, interface, port)).join(flow)

  def wrapStage(settings: Settings, interface: String, port: Int) = new GraphStage[BidiShape[HttpRequest, HttpRequest, HttpResponse, HttpResponse]] {
    val httpServerConfig = Kamon.config().getConfig(settings.configPath)
    val httpServerInstrumentation = HttpServerInstrumentation.from(httpServerConfig, settings.component, interface, port)
    val requestIn = Inlet.create[HttpRequest]("request.in")
    val requestOut = Outlet.create[HttpRequest]("request.out")
    val responseIn = Inlet.create[HttpResponse]("response.in")
    val responseOut = Outlet.create[HttpResponse]("response.out")

    override val shape = BidiShape(requestIn, requestOut, responseIn, responseOut)

    override def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) {

      // There might be more than one outstanding request when HTTP pipelining is enabled but according to the Akka HTTP
      // documentation, it is required by the applications to generate responses for all requests and to generate them
      // in the appropriate order, so we can simply queue and dequeue the RequestHandlers as the requests flow through
      // the Stage.
      //
      // More info: https://doc.akka.io/docs/akka-http/current/server-side/low-level-api.html#request-response-cycle
      private val _pendingRequests = mutable.Queue.empty[RequestHandler]
      private val _createdAt = Kamon.clock().instant()
      private var _completedRequests = 0

      setHandler(requestIn, new InHandler {
        override def onPush(): Unit = {
          val request = grab(requestIn)
          val requestHandler = httpServerInstrumentation.createHandler(toRequest(request), deferSamplingDecision = true)
            .requestReceived()

          val defaultOperationName = httpServerInstrumentation.settings.defaultOperationName
          val requestSpan = requestHandler.span

          // Automatic Operation name changes will only be allowed if the initial name HTTP Server didn't assign a
          // user-defined name to the operation (that would be, either via an Operation Name Generator or a custom
          // operation mapping).
          val allowAutomaticChanges = requestSpan.operationName() == defaultOperationName

          _pendingRequests.enqueue(requestHandler)

          // The only reason why it's safe to leave the Thread dirty is because the Actor
          // instrumentation will cleanup afterwards.
          Kamon.storeContext(requestHandler.context.withEntry(
            LastAutomaticOperationNameEdit.Key, Option(LastAutomaticOperationNameEdit(requestSpan.operationName(), allowAutomaticChanges))
          ))

          push(requestOut, request)
        }

        override def onUpstreamFinish(): Unit =
          complete(requestOut)
      })

      setHandler(requestOut, new OutHandler {
        override def onPull(): Unit =
          pull(requestIn)

        override def onDownstreamFinish(t: Throwable): Unit =
          cancel(requestIn, t)
      })

      setHandler(responseIn, new InHandler {
        override def onPush(): Unit = {
          val response = grab(responseIn)
          val requestHandler = _pendingRequests.dequeue()
          val requestSpan = requestHandler.span
          val responseWithContext = requestHandler.buildResponse(toResponseBuilder(response), requestHandler.context)

          if(response.status.intValue() == 404 && requestSpan.operationName() == httpServerInstrumentation.settings.defaultOperationName) {

            // It might happen that if no route was able to handle the request or no directive that would force taking
            // a sampling decision was run, the request would still not have any sampling decision so we both set the
            // request as unhandled and take a sampling decision for that operation here.
            requestSpan
              .name(httpServerInstrumentation.settings.unhandledOperationName)
              .takeSamplingDecision()
          }

          val entity = if(responseWithContext.entity.isKnownEmpty()) {
            requestHandler.responseSent(0L)
            responseWithContext.entity
          } else {

            requestSpan.mark("http.response.ready")

            responseWithContext.entity match {
              case strict @ HttpEntity.Strict(_, bs) =>
                requestHandler.responseSent(bs.size)
                strict

              case default: HttpEntity.Default =>
                requestHandler.responseSent(default.contentLength)
                default

              case _ =>
                val responseSizeCounter = new AtomicLong(0L)
                responseWithContext.entity.transformDataBytes(
                  Flow[ByteString]
                    .watchTermination()(Keep.right)
                    .wireTap(bs => responseSizeCounter.addAndGet(bs.size))
                    .mapMaterializedValue { f =>
                      f.andThen {
                        case Success(_) =>
                          requestHandler.responseSent(responseSizeCounter.get())
                        case Failure(e) =>
                          requestSpan.fail("Response entity stream failed", e)
                          requestHandler.responseSent(responseSizeCounter.get())

                      }(CallingThreadExecutionContext)
                    }
                )
            }
          }

          _completedRequests += 1
          push(responseOut, responseWithContext.withEntity(entity))
        }

        override def onUpstreamFinish(): Unit =
          completeStage()
      })

      setHandler(responseOut, new OutHandler {
        override def onPull(): Unit =
          pull(responseIn)

        override def onDownstreamFinish(t: Throwable): Unit =
          cancel(responseIn, t)
      })

      override def preStart(): Unit =
        httpServerInstrumentation.connectionOpened()

      override def postStop(): Unit = {
        val connectionLifetime = Duration.between(_createdAt, Kamon.clock().instant())
        httpServerInstrumentation.connectionClosed(connectionLifetime, _completedRequests)
      }
    }
  }

  def changeSettings(component: String, configPath: String): Unit =
    _wrapperSettings = Settings(component, configPath)

  def resetSettings(): Unit =
    _wrapperSettings = _defaultSettings

  def defaultOperationName(listenPort: Int): String =
    _defaultOperationNames.getOrElseUpdate(listenPort, {
      _serverInstrumentations.get(listenPort).map(_.settings.defaultOperationName).getOrElse("http.server.request")
    })

  case class Settings(component: String, configPath: String)
}
