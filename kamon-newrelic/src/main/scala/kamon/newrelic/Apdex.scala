package kamon.newrelic

import akka.actor.Actor
import kamon.trace.UowTrace
import com.newrelic.api.agent.{NewRelic => NRAgent}
import kamon.trace.UowTracing.WebExternal

class Apdex extends Actor {
  val t = 500

  var satisfied: Int = 0
  var tolerating: Int = 0
  var frustrated: Int = 0

  def receive = {
    case trace: UowTrace => recordTransaction(trace)

  }

  def clearStats: Unit = {
    satisfied = 0
    tolerating = 0
    frustrated = 0
  }

  def total: Int = satisfied + tolerating + frustrated

  def updateStats(sampleTime: Double): Unit = {
    if(sampleTime < t)
      satisfied += 1
    else
      if(sampleTime >= t && sampleTime <= 4*t)
        tolerating += 1
      else
        frustrated += 1
  }

  def recordTransaction(uowTrace: UowTrace): Unit = {
    val time = ((uowTrace.segments.last.timestamp - uowTrace.segments.head.timestamp)/1E9)

    updateStats(time)

    NRAgent.recordMetric("WebTransaction/Custom" + uowTrace.name, time.toFloat )
    NRAgent.recordMetric("WebTransaction", time.toFloat)
    NRAgent.recordMetric("HttpDispatcher", time.toFloat)

    uowTrace.segments.collect { case we: WebExternal => we }.foreach { webExternalTrace =>
      val external = ((webExternalTrace.finish - webExternalTrace.start)/1E9).toFloat

      println("Web External: " + webExternalTrace)
      NRAgent.recordMetric(s"External/${webExternalTrace.host}/http", external)
      NRAgent.recordMetric(s"External/${webExternalTrace.host}/all", external)
      NRAgent.recordMetric(s"External/${webExternalTrace.host}/http/WebTransaction/Custom" + uowTrace.name, external)
    }


    val allExternals = uowTrace.segments.collect { case we: WebExternal =>  we } sortBy(_.timestamp)


    def measureExternal(accum: Long, lastEnd: Long, segments: Seq[WebExternal]): Long = segments match {
      case Nil          => accum
      case head :: tail =>
        if(head.start > lastEnd)
          measureExternal(accum + (head.finish-head.start), head.finish, tail)
        else
          measureExternal(accum + (head.finish-lastEnd), head.finish, tail)
    }

    val external = measureExternal(0, 0, allExternals) / 1E9


    NRAgent.recordMetric(s"External/all", external.toFloat)
    NRAgent.recordMetric(s"External/allWeb", external.toFloat)

  }
}

case object Flush
