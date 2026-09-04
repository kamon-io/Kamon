/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
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

package kamon.instrumentation.cassandra.driver

import java.nio.ByteBuffer
import java.util

import com.datastax.driver.core._
import com.google.common.base.Function
import com.google.common.util.concurrent.{
  FutureCallback,
  Futures,
  ListenableFuture,
  ListeningExecutorService,
  MoreExecutors
}
import kamon.Kamon
import kamon.instrumentation.cassandra.CassandraInstrumentation
import kamon.trace.Span

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

class InstrumentedSession(underlying: Session) extends AbstractSession {
  import InstrumentedSession._

  private val executor = unsafeExtractExecutor(underlying)

  override def getLoggedKeyspace: String =
    underlying.getLoggedKeyspace

  override def init(): Session =
    new InstrumentedSession(underlying.init())

  override def initAsync(): ListenableFuture[Session] = {
    Futures.transform(
      underlying.initAsync(),
      new Function[Session, Session] {
        override def apply(session: Session): Session =
          new InstrumentedSession(session)
      },
      executor
    )
  }

  override def prepareAsync(
    query: String,
    customPayload: util.Map[String, ByteBuffer]
  ): ListenableFuture[PreparedStatement] = {
    val statement = new SimpleStatement(query)
    statement.setOutgoingPayload(customPayload)
    underlying.prepareAsync(statement)
  }

  private def buildClientSpan(statement: Statement): Span =
    if (CassandraInstrumentation.settings.enableTracing) {
      val query = extractQuery(statement)
      val statementKind = extractStatementType(query)

      val clientSpan = Kamon
        .clientSpanBuilder(QueryOperations.QueryOperationName, "cassandra.driver")
        .tagMetrics("cassandra.query.kind", statementKind.getOrElse("other"))
        .tag("db.statement", query)
        .tag("db.type", "cassandra")
        .start()

      Option(statement.getKeyspace).foreach(ks => clientSpan.tag("db.instance", ks))

      clientSpan
    } else Span.Empty

  override def executeAsync(statement: Statement): ResultSetFuture = {
    val clientSpan = buildClientSpan(statement)

    val future = Try(
      Kamon.runWithContext(Kamon.currentContext().withEntry(Span.Key, clientSpan)) {
        underlying.executeAsync(statement)
      }
    ) match {
      case Success(resultSetFuture) => resultSetFuture
      case Failure(cause) =>
        clientSpan.fail(cause.getMessage, cause)
        clientSpan.finish()
        throw cause
    }

    Futures.addCallback(
      future,
      new FutureCallback[ResultSet] {
        override def onSuccess(result: ResultSet): Unit = {
          recordClientQueryExecutionInfo(clientSpan, result)
          clientSpan.finish()
        }

        override def onFailure(cause: Throwable): Unit = {
          clientSpan
            .fail(cause.getMessage, cause)
            .finish()
        }
      },
      MoreExecutors.directExecutor()
    )
    future
  }

  private def recordClientQueryExecutionInfo(clientSpan: Span, result: ResultSet): Unit = {
    val info = result.getExecutionInfo
    val hasMore = !result.isFullyFetched

    val trace = info.getQueryTrace
    if (trace != null) {

      // NOTE: Do not access any member other than traceId from the trace object. Accessing any other member on the
      //       trace object will trigger a blocking round trip to the Cassandra server to fetch the trace data.
      clientSpan.tag("cassandra.driver.rs.trace-id", trace.getTraceId.toString)
    }

    val consistencyLevel = info.getAchievedConsistencyLevel
    if (consistencyLevel != null) {
      clientSpan.tag("cassandra.driver.rs.consistency-level", consistencyLevel.name())
    }

    clientSpan
      .tag("cassandra.driver.rs.fetch-size", info.getStatement.getFetchSize)
      .tag("cassandra.driver.rs.fetched", result.getAvailableWithoutFetching)
      .tag("cassandra.driver.rs.has-more", hasMore)
  }

  override def closeAsync(): CloseFuture =
    underlying.closeAsync()

  override def isClosed: Boolean =
    underlying.isClosed

  override def getCluster: Cluster =
    underlying.getCluster

  override def getState: Session.State =
    underlying.getState

  private def extractQuery(statement: Statement): String = statement match {
    case b: BoundStatement =>
      b.preparedStatement.getQueryString
    case r: RegularStatement =>
      r.getQueryString
    case batchStatement: BatchStatement =>
      batchStatement.getStatements.asScala.map(extractQuery).mkString(",")
    case _ => "unsupported-statement-type"
  }

  /** Try extracting type of a DML statement based on query string prefix.
    * It could be done matching on QueryBuilder statement subtypes but fails on SimpleStatements
    * http://cassandra.apache.org/doc/latest/cql/dml.html
    *
    * @param query query string
    * @return dml statement type, none if not a dml statement
    */
  private def extractStatementType(query: String): Option[String] = {
    Try(query.substring(0, query.indexOf(" ")).toLowerCase)
      .filter(DmlStatementPrefixes.contains)
      .toOption
  }

  private def unsafeExtractExecutor(session: Session): ListeningExecutorService = {
    val executorMethod = Class.forName("com.datastax.driver.core.SessionManager")
      .getDeclaredMethod("executor")

    executorMethod.setAccessible(true)
    executorMethod.invoke(session).asInstanceOf[ListeningExecutorService]
  }
}

object InstrumentedSession {
  val DmlStatementPrefixes = Set("select", "insert", "update", "delete")
}
