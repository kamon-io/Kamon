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
 * taken from: http://pastebin.com/DHVb54iK @jypma
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

  private def includeTraceToken(response: HttpResponse, traceTokenHeaderName: String, token: String): HttpResponse =
    response match {
      case response: HttpResponse ⇒ response.withHeaders(response.headers ++ Seq(RawHeader(traceTokenHeaderName, token)))
      case other                  ⇒ other
    }
}

