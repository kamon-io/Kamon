/* ===================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */
package kamon.newrelic

import akka.actor.Actor
import kamon.trace.UowTrace
import com.newrelic.api.agent.NewRelic
import kamon.trace.UowTracing.WebExternal


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
