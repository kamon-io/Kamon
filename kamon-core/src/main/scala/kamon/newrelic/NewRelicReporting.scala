package kamon.newrelic

import akka.actor.Actor
import kamon.trace.UowTrace
import com.newrelic.api.agent.{Response, Request, Trace, NewRelic}
import kamon.trace.UowTracing.{WebExternal, WebExternalFinish, WebExternalStart}
import java.util
import java.util.Date


class NewRelicReporting extends Actor {
  def receive = {
    case trace: UowTrace => recordTransaction(trace)
  }

  def recordTransaction(uowTrace: UowTrace): Unit = {
    val time = ((uowTrace.segments.last.timestamp - uowTrace.segments.head.timestamp)/1E9)

    NewRelic.recordMetric("WebTransaction/Custom" + uowTrace.name, time.toFloat )
    NewRelic.recordMetric("WebTransaction", time.toFloat)
    NewRelic.recordMetric("HttpDispatcher", time.toFloat)

    uowTrace.segments.collect { case we: WebExternal => we }.foreach { webExternalTrace =>
      val external = ((webExternalTrace.finish - webExternalTrace.start)/1E9).toFloat

      println("Web External: " + webExternalTrace)
      NewRelic.recordMetric(s"External/${webExternalTrace.host}/http", external)
      NewRelic.recordMetric(s"External/${webExternalTrace.host}/all", external)
      NewRelic.recordMetric(s"External/${webExternalTrace.host}/http/WebTransaction/Custom" + uowTrace.name, external)
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


    NewRelic.recordMetric(s"External/all", external.toFloat)
    NewRelic.recordMetric(s"External/allWeb", external.toFloat)

  }
}
