package kamon.jdbc.instrumentation

import kamon.jdbc.metric.StatementMetrics

object HasStatementMetrics {
  trait Mixin {
    def statementMetrics: StatementMetrics
    def setStatementMetrics(statementMetrics: StatementMetrics): Unit
  }

  def apply(): HasStatementMetrics.Mixin = new HasStatementMetrics.Mixin() {
    @volatile private var entity: StatementMetrics = _
    override def statementMetrics: StatementMetrics = entity
    override def setStatementMetrics(sm: StatementMetrics): Unit = this.entity = sm
  }
}