package kamon.newrelic

import akka.actor.Actor
import kamon.trace.UowTrace
import com.newrelic.api.agent.{Trace, NewRelic}


class NewRelicReporting extends Actor {
  def receive = {
    case trace: UowTrace => recordTransaction(trace)
  }

  //@Trace
  def recordTransaction(uowTrace: UowTrace): Unit = {
    val time = (uowTrace.segments.last.timestamp - uowTrace.segments.head.timestamp)/1E9

    NewRelic.recordMetric("WebTransaction/Custom" + uowTrace.name, time.toFloat)
    NewRelic.recordMetric("WebTransaction", time.toFloat)
    NewRelic.recordMetric("HttpDispatcher", time.toFloat)
  }
}
