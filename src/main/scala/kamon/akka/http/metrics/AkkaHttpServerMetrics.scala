package kamon.akka.http.metrics

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import kamon.metric.instrument.InstrumentFactory
import kamon.metric.{EntityRecorderFactoryCompanion, GenericEntityRecorder}
import kamon.util.logger.LazyLogger

class AkkaHttpServerMetrics(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {
  val log = LazyLogger(classOf[AkkaHttpServerMetrics])

  val statusInformational = counter("response-status-informational")
  val statusSuccess = counter("response-status-success")
  val statusRedirection = counter("response-status-redirection")
  val statusClientError = counter("response-status-clienterror")
  val statusServerError = counter("response-status-servererror")
  val requestActive = minMaxCounter("request-active")
  val connectionOpen = minMaxCounter("connection-open")

  def recordRequest() = {
    log.debug("Logging a request")
    requestActive.increment()
  }

  def recordResponse(response:HttpResponse):Unit = {
    requestActive.decrement()

    response.status match {
      case StatusCodes.Informational(_) => statusInformational.increment()
      case StatusCodes.Success(_) => statusSuccess.increment()
      case StatusCodes.Redirection(_) => statusRedirection.increment()
      case StatusCodes.ClientError(_) => statusClientError.increment()
      case StatusCodes.ServerError(_) =>  statusServerError.increment()
      case other =>
    }
  }

  def recordConnectionOpened():Unit = connectionOpen.increment()
  def recordConnectionClosed():Unit = connectionOpen.decrement()
}

object AkkaHttpServerMetrics extends EntityRecorderFactoryCompanion[AkkaHttpServerMetrics]("akka-http-server", new AkkaHttpServerMetrics(_))
