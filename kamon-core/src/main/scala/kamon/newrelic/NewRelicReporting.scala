package kamon.newrelic

import akka.actor.Actor
import kamon.trace.UowTrace
import com.newrelic.api.agent.{Trace, NewRelic}
import kamon.trace.UowTracing.WebExternal


class NewRelicReporting extends Actor {
  def receive = {
    case trace: UowTrace => recordTransaction(trace)
  }

  //@Trace
  def recordTransaction(uowTrace: UowTrace): Unit = {
    val time = ((uowTrace.segments.last.timestamp - uowTrace.segments.head.timestamp)/1E9)

    NewRelic.recordMetric("WebTransaction/Custom" + uowTrace.name, time.toFloat )
    NewRelic.recordMetric("WebTransaction", time.toFloat)
    NewRelic.recordMetric("HttpDispatcher", time.toFloat)

    uowTrace.segments.collect { case we: WebExternal => we }.foreach { webExternalTrace =>
      val external = ((webExternalTrace.end - webExternalTrace.start)/1E9).toFloat
      NewRelic.recordMetric(s"External/all", external)
      NewRelic.recordMetric(s"External/allWeb", external)

      NewRelic.recordMetric(s"Solr/all", 0.1F)
      NewRelic.recordMetric(s"Solr/allWeb", 0.1F)
      NewRelic.recordMetric(s"Solr/set", 0.1F)
      NewRelic.recordMetric(s"Solr/set/WebTransaction/Custom/test", 0.1F)

      NewRelic.recordMetric(s"External/${webExternalTrace.host}/http", external)
      NewRelic.recordMetric(s"External/${webExternalTrace.host}/all", external)
      NewRelic.recordMetric(s"External/${webExternalTrace.host}/http/" + "WebTransaction/Custom" + uowTrace.name, external)


    }

  }
}
