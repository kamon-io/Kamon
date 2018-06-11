/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.jdbc.instrumentation

import java.time.Instant
import java.time.temporal.ChronoUnit

import kamon.Kamon
import kamon.Kamon.buildSpan
import kamon.jdbc.{Jdbc, Metrics}
import kamon.jdbc.instrumentation.mixin.HasConnectionPoolMetrics
import kamon.metric.RangeSampler
import kamon.trace.{Span, SpanCustomizer}

object StatementMonitor {
  object StatementTypes {
    val Query = "query"
    val Update = "update"
    val Batch = "batch"
    val GenericExecute = "generic-execute"
  }

  def trackStart(target: Any,
                 sql: String,
                 statementType: String): (Span, String, Instant, RangeSampler) = {

    val poolTags = Option(target.asInstanceOf[HasConnectionPoolMetrics].connectionPoolMetrics)
      .map(_.tags)
      .getOrElse(Map.empty[String, String])

    val inFlight = Metrics.Statements.InFlight.refine(poolTags)
    inFlight.increment()

    val startTimestamp = Kamon.clock().instant()
    val span = Kamon.currentContext().get(SpanCustomizer.ContextKey).customize {
      val builder = buildSpan(statementType)
        .withFrom(startTimestamp)
        .withTag("component", "jdbc")
        .withTag("db.statement", sql)

      poolTags.foreach { case (key, value) => builder.withTag(key, value) }
      builder
    }.start()

    (span, sql, startTimestamp, inFlight)
  }

  def trackEnd(traveler:(Span, String, Instant, RangeSampler),
               throwable: Throwable): Unit = {

    val (span, sql, startTimestamp, inFlight) = traveler

    if(throwable != null) {
      span.addError("error.object", throwable)
      Jdbc.onStatementError(sql, throwable)
    }

    val endTimestamp = Kamon.clock().instant()
    val elapsedTime = startTimestamp.until(endTimestamp, ChronoUnit.MICROS)
    span.finish(endTimestamp)
    inFlight.decrement()

    Jdbc.onStatementFinish(sql, elapsedTime)
  }
}

