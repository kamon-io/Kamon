package kamon.http

import kamon.metric.{ EntityRecorderFactory, GenericEntityRecorder }
import kamon.metric.instrument.InstrumentFactory

/**
 *  Counts HTTP response status codes into per status code and per trace name + status counters. If recording a HTTP
 *  response with status 500 for the trace "GetUser", the counter with name "500" as well as the counter with name
 *  "GetUser_500" will be incremented.
 */
class HttpServerMetrics(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {

  def recordResponse(statusCode: String): Unit =
    counter(statusCode).increment()

  def recordResponse(traceName: String, statusCode: String): Unit = {
    recordResponse(statusCode)
    counter(traceName + "_" + statusCode).increment()
  }
}

object HttpServerMetrics extends EntityRecorderFactory[HttpServerMetrics] {
  def category: String = "http-server"
  def createRecorder(instrumentFactory: InstrumentFactory): HttpServerMetrics = new HttpServerMetrics(instrumentFactory)
}
