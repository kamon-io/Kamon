/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon.instrumentation.jdbc

import java.time.Instant
import java.time.temporal.ChronoUnit

import com.typesafe.config.Config

import kamon.Kamon
import kamon.instrumentation.jdbc.utils.LoggingSupport
import kamon.metric.RangeSampler
import kamon.tag.{Lookups, TagSet}
import kamon.trace.Span
import kanela.agent.bootstrap.stack.CallStackDepth
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.schema.Table
import net.sf.jsqlparser.statement.alter.Alter
import net.sf.jsqlparser.statement.alter.sequence.AlterSequence
import net.sf.jsqlparser.statement.comment.Comment
import net.sf.jsqlparser.statement.create.index.CreateIndex
import net.sf.jsqlparser.statement.create.schema.CreateSchema
import net.sf.jsqlparser.statement.create.sequence.CreateSequence
import net.sf.jsqlparser.statement.create.synonym.CreateSynonym
import net.sf.jsqlparser.statement.create.table.CreateTable
import net.sf.jsqlparser.statement.create.view.{AlterView, CreateView}
import net.sf.jsqlparser.statement.delete.Delete
import net.sf.jsqlparser.statement.drop.Drop
import net.sf.jsqlparser.statement.execute.Execute
import net.sf.jsqlparser.statement.grant.Grant
import net.sf.jsqlparser.statement.insert.Insert
import net.sf.jsqlparser.statement.merge.Merge
import net.sf.jsqlparser.statement.replace.Replace
import net.sf.jsqlparser.statement.select.{FromItemVisitor, LateralSubSelect, ParenthesisFromItem, PlainSelect, Select, SelectVisitor, SetOperationList, SubJoin, SubSelect, TableFunction, ValuesList, WithItem}
import net.sf.jsqlparser.statement.show.ShowTablesStatement
import net.sf.jsqlparser.statement.truncate.Truncate
import net.sf.jsqlparser.statement.update.Update
import net.sf.jsqlparser.statement.upsert.Upsert
import net.sf.jsqlparser.statement.values.ValuesStatement
import net.sf.jsqlparser.statement.{Block, Commit, CreateFunctionalStatement, DeclareStatement, DescribeStatement, ExplainStatement, SetStatement, ShowColumnsStatement, ShowStatement, StatementVisitor, Statements, UseStatement}



class SqlVisitor(var operation: String) extends StatementVisitor with FromItemVisitor with SelectVisitor  {

  override def visit(comment: Comment): Unit = {}

  override def visit(commit: Commit): Unit = operation = "commit"

  override def visit(delete: Delete): Unit = operation = s"delete ${delete.getTable.toString}"

  override def visit(update: Update): Unit = operation = s"update ${update.getTable.toString}"

  override def visit(insert: Insert): Unit = operation = s"insert ${insert.getTable.toString}"

  override def visit(replace: Replace): Unit = operation = s"replace ${replace.getTable.toString}"

  override def visit(drop: Drop): Unit = operation = s"drop ${drop.getName.toString}"

  override def visit(truncate: Truncate): Unit = operation = s"truncate ${truncate.getTable.toString}"

  override def visit(createIndex: CreateIndex): Unit = operation = s"create_index ${createIndex.getIndex.getName}"

  override def visit(aThis: CreateSchema): Unit = operation = s"create_schema ${aThis.getSchemaName}"

  override def visit(createTable: CreateTable): Unit = operation = s"create_table ${createTable.getTable.toString}"

  override def visit(createView: CreateView): Unit = operation = s"create_view ${createView.getView.toString}"

  override def visit(alterView: AlterView): Unit = operation = s"alter_view ${alterView.getView.toString}"

  override def visit(alter: Alter): Unit = operation = s"alter ${alter.getTable.toString}"

  override def visit(stmts: Statements): Unit = {}

  override def visit(execute: Execute): Unit = {}

  override def visit(set: SetStatement): Unit = {}

  override def visit(set: ShowColumnsStatement): Unit = {}

  override def visit(showTables: ShowTablesStatement): Unit = {}

  override def visit(merge: Merge): Unit = {}

  override def visit(select: Select): Unit = select.getSelectBody.accept(this)

  override def visit(plainSelect: PlainSelect): Unit = plainSelect.getFromItem.accept(this)

  override def visit(tableName: Table): Unit = operation = s"select ${tableName.toString}"

  override def visit(subSelect: SubSelect): Unit = operation = s"select ${subSelect.getAlias.toString}"

  override def visit(subjoin: SubJoin): Unit = operation = s"select ${subjoin.getAlias.toString}"

  override def visit(lateralSubSelect: LateralSubSelect): Unit = operation = s"select ${lateralSubSelect.getAlias.toString}"

  override def visit(valuesList: ValuesList): Unit = operation = s"select ${valuesList.getAlias}"

  override def visit(tableFunction: TableFunction): Unit = operation = s"select ${tableFunction.getAlias}"

  override def visit(aThis: ParenthesisFromItem): Unit = operation = s"select ${aThis.getAlias.toString}"

  override def visit(setOpList: SetOperationList): Unit = {}

  override def visit(withItem: WithItem): Unit = {}

  override def visit(upsert: Upsert): Unit = operation = s"upsert ${upsert.getTable.toString}"

  override def visit(use: UseStatement): Unit = {}

  override def visit(block: Block): Unit = {}

  override def visit(values: ValuesStatement): Unit = operation = "values"

  override def visit(describe: DescribeStatement): Unit = {}

  override def visit(aThis: ExplainStatement): Unit = operation = "explain"

  override def visit(aThis: ShowStatement): Unit = operation = "show"

  override def visit(aThis: DeclareStatement): Unit = {}

  override def visit(grant: Grant): Unit = operation = "grant"

  override def visit(createSequence: CreateSequence): Unit = operation = s"create_sequnce ${createSequence.sequence.getName}"

  override def visit(alterSequence: AlterSequence): Unit = operation = s"alter_sequence ${alterSequence.sequence.getName}"

  override def visit(createFunctionalStatement: CreateFunctionalStatement): Unit = {}

  override def visit(createSynonym: CreateSynonym): Unit = {}
}

object StatementMonitor extends LoggingSupport {

  object StatementTypes {
    val Query = "query"
    val Update = "update"
    val Batch = "batch"
    val GenericExecute = "execute"
  }

  @volatile private var parseSqlOperationName: Boolean = getParseSqlOperationName(Kamon.config())

  Kamon.onReconfigure{ config =>
    parseSqlOperationName = getParseSqlOperationName(config)
  }

  private def getParseSqlOperationName(config: Config) = {
    config.getBoolean("kamon.modules.jdbc.parse-sql-for-operation-name")
  }

  def start(statement: Any, sql: String, statementType: String): Option[Invocation] = {
    if (CallStackDepth.incrementFor(statement) == 0) {
      val startTimestamp = Kamon.clock().instant()

      // It could happen that there is no Pool Telemetry on the Pool when fail-fast is enabled and a connection is
      // created while the Pool's constructor is still executing.
      val (inFlightRangeSampler: RangeSampler, databaseTags: DatabaseTags) = statement match {
        case cpt: HasConnectionPoolTelemetry if cpt.connectionPoolTelemetry != null && cpt.connectionPoolTelemetry.get() != null =>
          val poolTelemetry = cpt.connectionPoolTelemetry.get()
          (poolTelemetry.instruments.inFlightStatements, poolTelemetry.databaseTags)

        case dbt: HasDatabaseTags if dbt.databaseTags() != null =>
          (JdbcMetrics.InFlightStatements.withTags(dbt.databaseTags().metricTags), dbt.databaseTags())

        case _ =>
          (JdbcMetrics.InFlightStatements.withoutTags(), DatabaseTags(TagSet.Empty, TagSet.Empty))
      }

      val clientSpan = Kamon.clientSpanBuilder(
        if (parseSqlOperationName) {
          try {
            val startTime = System.nanoTime()
            val statement = CCJSqlParserUtil.parse(sql)
            val statementVisitor = new SqlVisitor(statementType)
            statement.accept(statementVisitor)
            val operationTimeMs = (System.nanoTime() - startTime) / 1000000
            logDebug(s"Operation parsing took ${operationTimeMs}ms")
            statementVisitor.operation
          } catch {
            case e: Exception =>
              if (logger.isInfoEnabled) {
                logger.info(s"Could not parse sql to get operation name: ${sql}", e)
              }
              statementType
          }
        } else {
          statementType
        },
        "jdbc"
      ).tag("db.statement", sql)

      databaseTags.spanTags.iterator().foreach(t => clientSpan.tag(t.key, databaseTags.spanTags.get(Lookups.coerce(t.key))))
      databaseTags.metricTags.iterator().foreach(t => clientSpan.tagMetrics(t.key, databaseTags.metricTags.get(Lookups.coerce(t.key))))
      inFlightRangeSampler.increment()

      Some(Invocation(statement, clientSpan.start(startTimestamp), sql, startTimestamp, inFlightRangeSampler))
    } else None
  }

  case class Invocation(statement: Any, span: Span, sql: String, startedAt: Instant, inFlight: RangeSampler) {

    def close(throwable: Throwable): Unit = {
      if (throwable != null) {
        span.fail(throwable)
        JdbcInstrumentation.onStatementFailure(sql, throwable)
      }

      inFlight.decrement()
      val endedAt = Kamon.clock().instant()
      val elapsedTime = startedAt.until(endedAt, ChronoUnit.NANOS)
      span.finish(endedAt)

      JdbcInstrumentation.onStatementFinish(sql, elapsedTime)
      CallStackDepth.resetFor(statement)
    }
  }
}
