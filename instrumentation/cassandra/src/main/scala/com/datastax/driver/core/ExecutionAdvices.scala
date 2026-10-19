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

package com.datastax.driver.core

import java.util.concurrent.atomic.AtomicReference
import com.datastax.driver.core.Message.Response
import com.datastax.driver.core.RequestHandler.QueryState
import kamon.Kamon
import kamon.context.Context
import kamon.context.Storage.Scope
import kamon.instrumentation.cassandra.CassandraInstrumentation
import kamon.instrumentation.cassandra.CassandraInstrumentation.Tags
import kamon.instrumentation.cassandra.driver.DriverInstrumentation.ClusterManagerBridge
import kamon.instrumentation.cassandra.metrics.{HasPoolMetrics, NodeMonitor}
import kamon.instrumentation.context.HasContext
import kamon.trace.Span
import kanela.agent.libs.net.bytebuddy.asm.Advice

import scala.annotation.static

object QueryOperations {
  val QueryOperationName = "cassandra.query"
  val BatchOperationName = "cassandra.batch"
  val QueryPrepareOperationName: String = QueryOperationName + ".prepare"
  val ExecutionOperationName: String = QueryOperationName + ".execution"
}

class QueryExecutionAdvice
object QueryExecutionAdvice {
  import QueryOperations._

  val ParentSpanKey: Context.Key[Span] = Context.key[Span]("__parent-span", Span.Empty)

  @Advice.OnMethodEnter
  @static def onQueryExec(
    @Advice.This execution: HasContext,
    @Advice.Argument(0) host: Host with HasPoolMetrics,
    @Advice.FieldValue("position") position: Int,
    @Advice.FieldValue("queryStateRef") queryState: AtomicReference[QueryState]
  ): Unit = {
    val nodeMonitor = host.nodeMonitor

    val clientSpan = Kamon.currentSpan()
    val executionSpan = if (CassandraInstrumentation.settings.createRoundTripSpans) {
      Kamon
        .clientSpanBuilder(ExecutionOperationName, Tags.CassandraDriverComponent)
        .asChildOf(clientSpan)
        .start()
    } else Span.Empty

    val isSpeculative = position > 0
    if (isSpeculative) {
      nodeMonitor.speculativeExecution()
      executionSpan.tag("cassandra.speculative", true)
    }

    if (queryState.get().isCancelled) {
      nodeMonitor.cancellation()
    }

    host.nodeMonitor.executionStarted()
    nodeMonitor.applyNodeTags(executionSpan)

    val executionContext = execution.context
      .withEntry(Span.Key, executionSpan)
      .withEntry(ParentSpanKey, clientSpan)

    execution.setContext(executionContext)
  }
}

/**
  * Transfer context from msg to created result set so it can be used for further page fetches
  */
class OnResultSetConstruction
object OnResultSetConstruction {

  @Advice.OnMethodExit
  @static def onCreateResultSet(
    @Advice.Return rs: ArrayBackedResultSet,
    @Advice.Argument(0) msg: Responses.Result with HasContext
  ): Unit = if (rs.isInstanceOf[HasContext]) {
    rs.asInstanceOf[HasContext].setContext(msg.context)
  }

}

class OnFetchMore
object OnFetchMore {

  @Advice.OnMethodEnter
  @static def onFetchMore(@Advice.This hasContext: HasContext): Scope = {
    val clientSpan = hasContext.context.get(QueryExecutionAdvice.ParentSpanKey)
    Kamon.storeContext(Context.of(Span.Key, clientSpan))
  }

  @Advice.OnMethodExit
  @static def onFetched(@Advice.Enter scope: Scope): Unit = {
    scope.close()
  }
}

class QueryWriteAdvice
object QueryWriteAdvice {

  @Advice.OnMethodEnter
  @static def onStartWriting(@Advice.This execution: HasContext): Unit = {
    execution.context
      .get(Span.Key)
      .mark("cassandra.connection.write-started")
  }
}

//Server timeouts and exceptions
class OnSetAdvice
object OnSetAdvice {
  import QueryOperations._

  @Advice.OnMethodEnter
  @static def onSetResult(
    @Advice.This execution: Connection.ResponseCallback with HasContext,
    @Advice.Argument(0) connection: Connection,
    @Advice.Argument(1) response: Message.Response,
    @Advice.FieldValue("current") currentHost: Host with HasPoolMetrics
  ): Unit = {

    val executionSpan = execution.context.get(Span.Key)

    if (response.isInstanceOf[Responses.Result.Prepared]) {
      executionSpan.name(QueryPrepareOperationName)
    }

    if (execution.retryCount() > 0) {
      executionSpan.tag("cassandra.retry", true)
      currentHost.nodeMonitor.retry()
    }

    if (response.`type` == Response.Type.ERROR) {
      executionSpan.fail(response.`type`.name())
      currentHost.nodeMonitor.serverError()
    }

    currentHost.nodeMonitor.executionComplete()

    // In order to correlate paging requests with initial one, carry context with message
    response.asInstanceOf[HasContext].setContext(execution.context)
    executionSpan.finish()
  }
}

/**
  * Handling of client exceptions
  */
class OnExceptionAdvice
object OnExceptionAdvice {

  @Advice.OnMethodEnter
  @static def onException(
    @Advice.This execution: HasContext,
    @Advice.Argument(0) connection: Connection,
    @Advice.Argument(1) exception: Exception,
    @Advice.FieldValue("current") currentHost: Host with HasPoolMetrics
  ): Unit = {

    currentHost.nodeMonitor.clientError()
    currentHost.nodeMonitor.executionComplete()
    execution.context
      .get(Span.Key)
      .fail(exception)
      .finish()
  }
}

/**
  * Handling of client timeouts
  */
class OnTimeoutAdvice
object OnTimeoutAdvice {

  @Advice.OnMethodEnter
  @static def onTimeout(
    @Advice.This execution: HasContext,
    @Advice.Argument(0) connection: Connection,
    @Advice.FieldValue("current") currentHost: Host with HasPoolMetrics
  ): Unit = {

    currentHost.nodeMonitor.timeout()
    currentHost.nodeMonitor.executionComplete()
    execution.context
      .get(Span.Key)
      .fail("timeout")
      .finish()
  }
}

class HostLocationAdvice
object HostLocationAdvice {

  @Advice.OnMethodExit
  @static def onHostLocationUpdate(
    @Advice.This host: Host with HasPoolMetrics,
    @Advice.FieldValue("manager") clusterManager: Any
  ): Unit = {

    val targetHost = CassandraInstrumentation.createNode(host)
    host.setNodeMonitor(new NodeMonitor(targetHost))
  }
}
