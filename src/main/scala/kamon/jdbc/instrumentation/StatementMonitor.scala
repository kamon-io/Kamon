/* =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
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
import kamon.jdbc.instrumentation.mixin.HasConnectionPoolMetrics
import kamon.jdbc.utils.LoggingSupport
import kamon.jdbc.{Jdbc, Metrics}
import kamon.metric.RangeSampler
import kamon.trace.{Span, SpanCustomizer}
import kanela.agent.bootstrap.stack.CallStackDepth

object StatementMonitor extends LoggingSupport {
  object StatementTypes {
    val Query = "query"
    val Update = "update"
    val Batch = "batch"
    val GenericExecute = "generic-execute"
  }

  def start(target: Any, sql: String, statementType: String): Option[KamonMonitorTraveler] = {
    if (CallStackDepth.incrementFor(target) == 0) {
      val poolTags = extractPoolTags(target)
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

      Some(KamonMonitorTraveler(target, span, sql, startTimestamp, inFlight))
    } else None
  }

  case class KamonMonitorTraveler(target:Any, span: Span, sql: String, startTimestamp: Instant, inFlight: RangeSampler) {

    def close(throwable: Throwable): Unit = {

      if (throwable != null) {
        span.addError("error.object", throwable)
        Jdbc.onStatementError(sql, throwable)
      }

      val endTimestamp = Kamon.clock().instant()
      val elapsedTime = startTimestamp.until(endTimestamp, ChronoUnit.MICROS)
      span.finish(endTimestamp)
      inFlight.decrement()

      Jdbc.onStatementFinish(sql, elapsedTime)
      CallStackDepth.resetFor(target)
    }
  }

  private def extractPoolTags(target: Any): Map[String, String] = target match {
    case targetWithPoolMetrics: HasConnectionPoolMetrics =>
      Option(targetWithPoolMetrics.connectionPoolMetrics)
        .map(_.tags)
        .getOrElse(Map.empty[String, String])
    case _ =>
      logTrace(s"Statement is not a HasConnectionPoolMetrics type (used by kamon-jdbc). Target type: ${target.getClass.getTypeName}")
      Map.empty[String, String]
  }
}
