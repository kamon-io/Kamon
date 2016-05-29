package kamon.akka.http.metrics

import akka.http.scaladsl.model.{ HttpResponse, StatusCodes }
import kamon.metric.instrument.InstrumentFactory
import kamon.metric.{ EntityRecorderFactoryCompanion, GenericEntityRecorder }
import kamon.util.http.HttpServerMetrics
import kamon.util.logger.LazyLogger

class AkkaHttpServerMetrics(instrumentFactory: InstrumentFactory) extends HttpServerMetrics(instrumentFactory) {
  val log = LazyLogger(classOf[AkkaHttpServerMetrics])

  val requestActive = minMaxCounter("request-active")
  val connectionOpen = minMaxCounter("connection-open")

  def recordRequest() = {
    log.debug("Logging a request")
    requestActive.increment()
  }

  def recordResponse(response: HttpResponse, traceName: String): Unit = {
    requestActive.decrement()
    super.recordResponse(response.status.intValue.toString, traceName)
  }

  def recordConnectionOpened(): Unit = connectionOpen.increment()
  def recordConnectionClosed(): Unit = connectionOpen.decrement()
}

object AkkaHttpServerMetrics extends EntityRecorderFactoryCompanion[AkkaHttpServerMetrics]("akka-http-server", new AkkaHttpServerMetrics(_))
