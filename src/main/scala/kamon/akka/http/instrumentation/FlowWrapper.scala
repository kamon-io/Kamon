package kamon.akka.http.instrumentation

import akka.NotUsed
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream._
import akka.stream.scaladsl.{BidiFlow, Flow}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import kamon.Kamon
import kamon.akka.http.AkkaHttpExtension
import kamon.trace.Tracer
import kamon.util.logger.LazyLogger

/**
  * Wraps an {@code Flow[HttpRequest,HttpResponse]} with the necessary steps to output
  * the http metrics defined in AkkaHttpServerMetrics.
  * taken from: http://pastebin.com/DHVb54iK @jypma
  */
class FlowWrapper extends GraphStage[BidiShape[HttpRequest, HttpRequest, HttpResponse, HttpResponse]] {
  val requestIn = Inlet.create[HttpRequest]("request.in")
  val requestOut = Outlet.create[HttpRequest]("request.out")
  val responseIn = Inlet.create[HttpResponse]("response.in")
  val responseOut = Outlet.create[HttpResponse]("response.out")
  val bidiShape = BidiShape.of(requestIn, requestOut, responseIn, responseOut)

  override def shape: BidiShape[HttpRequest, HttpRequest, HttpResponse, HttpResponse] = bidiShape

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
    new KamonGraphStageLogic(requestIn, requestOut, responseIn, responseOut, shape)
  }
}

object FlowWrapper {
  def apply(flow:Flow[HttpRequest, HttpResponse, NotUsed]): Flow[HttpRequest, HttpResponse, NotUsed] = BidiFlow.fromGraph(new FlowWrapper).join(flow)
}

// Since the Flow is materialized once per HTTP connection, this GraphStageLogic will be as well.
// However, all connections log to the same kamon metrics instance.
class KamonGraphStageLogic(requestIn:Inlet[HttpRequest], requestOut:Outlet[HttpRequest], responseIn:Inlet[HttpResponse], responseOut:Outlet[HttpResponse], shape: Shape) extends GraphStageLogic(shape) {
  import KamonGraphStageLogic._

    setHandler(requestIn, new AbstractInHandler {
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

    setHandler(requestOut, new AbstractOutHandler {
      override def onPull(): Unit = pull(requestIn)
    })

    setHandler(responseIn, new AbstractInHandler {
      override def onPush(): Unit = {
        val response =  Tracer.currentContext.collect { ctx =>
          ctx.finish()

          val response = grab(responseIn)

          if (AkkaHttpExtension.settings.includeTraceTokenHeader)
            includeTraceToken(response, AkkaHttpExtension.settings.traceTokenHeaderName, ctx.token)
          else response

        } getOrElse grab(responseIn)

        metrics.recordResponse(response)

        push(responseOut, response)
      }
    })

    setHandler(responseOut, new AbstractOutHandler {
      override def onPull(): Unit = pull(responseIn)
    })

    override def preStart(): Unit = metrics.recordConnectionOpened()
    override def postStop(): Unit = metrics.recordConnectionClosed()
  }

object KamonGraphStageLogic {
  val log = LazyLogger(classOf[KamonGraphStageLogic])
  val metrics  = AkkaHttpExtension.metrics

  def includeTraceToken(response: HttpResponse, traceTokenHeaderName: String, token: String): HttpResponse =
    response match {
      case response: HttpResponse ⇒ response.withHeaders(response.headers ++ Seq(RawHeader(traceTokenHeaderName, token)))
      case other                  ⇒ other
    }
}

