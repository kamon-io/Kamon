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

package kamon
package instrumentation
package cassandra
package driver

import com.datastax.driver.core._
import com.datastax.oss.driver.api.core.cql
import com.datastax.oss.driver.api.core.cql.PrepareRequest
import com.datastax.oss.driver.internal.core.cql.{CqlPrepareHandler, CqlRequestHandler}
import kamon.instrumentation.cassandra.driver.DriverInstrumentation.ClusterManagerBridge
import kamon.instrumentation.cassandra.metrics.HasPoolMetrics
import kamon.instrumentation.context.HasContext
import kamon.trace.Span
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.api.instrumentation.bridge.FieldBridge
import kanela.agent.libs.net.bytebuddy.asm.Advice

import java.util.concurrent.CompletionStage
import java.util.function.BiConsumer
import scala.annotation.static

class DriverInstrumentation extends InstrumentationBuilder {

  /**
    * Wraps the client session with an InstrumentedSession.
    */
  onType("com.datastax.driver.core.Cluster$Manager")
    .intercept(method("newSession"), classOf[SessionInterceptor])
    .bridge(classOf[ClusterManagerBridge])

  /**
    * Instrument  connection pools (one per target host)
    * Pool size is incremented on pool init and when new connections are added
    * and decremented when connection is deemed defunct or explicitly trashed.
    * Pool metrics are mixed in the pool object itself
    */
  onType("com.datastax.driver.core.HostConnectionPool")
    .advise(method("borrowConnection"), classOf[BorrowAdvice])
    .advise(method("trashConnection"), classOf[TrashConnectionAdvice])
    .advise(method("addConnectionIfUnderMaximum"), classOf[CreateConnectionAdvice])
    .advise(method("onConnectionDefunct"), classOf[ConnectionDefunctAdvice])
    .advise(isConstructor, classOf[PoolConstructorAdvice])
    .advise(method("initAsync"), classOf[InitPoolAdvice])
    .advise(method("closeAsync"), classOf[PoolCloseAdvice])
    .mixin(classOf[HasPoolMetrics.Mixin])

  /**
    * Trace each query sub-execution as a child of client query,
    * this includes retries, speculative executions and fetchMore executions.
    * Once response is ready (onSet), context is carried via Message.Response mixin
    * to be used for further fetches
    */
  onType("com.datastax.driver.core.RequestHandler$SpeculativeExecution")
    .advise(method("query"), classOf[QueryExecutionAdvice])
    .advise(method("write"), classOf[QueryWriteAdvice])
    .advise(method("onException"), classOf[OnExceptionAdvice])
    .advise(method("onTimeout"), classOf[OnTimeoutAdvice])
    .advise(method("onSet"), classOf[OnSetAdvice])
    .mixin(classOf[HasContext.MixinWithInitializer])

  onSubTypesOf("com.datastax.driver.core.Message$Response")
    .mixin(classOf[HasContext.MixinWithInitializer])

  onType("com.datastax.driver.core.ArrayBackedResultSet")
    .advise(method("fromMessage"), classOf[OnResultSetConstruction])

  /**
    * In order for fetchMore execution to be a sibling of original execution
    * we need to carry parent-span id through result sets
    */
  onType("com.datastax.driver.core.ArrayBackedResultSet$MultiPage")
    .mixin(classOf[HasContext.MixinWithInitializer])
  onType("com.datastax.driver.core.ArrayBackedResultSet$MultiPage")
    .advise(method("queryNextPage"), classOf[OnFetchMore])

  /**
    * Query metrics are tagged with target information (based on config)
    * so all query metrics are mixed into a Host object
    */
  onType("com.datastax.driver.core.Host")
    .mixin(classOf[HasPoolMetrics.Mixin])
    .advise(method("setLocationInfo"), classOf[HostLocationAdvice])

  /**
    * Cassandra Driver 4.10 support
    */
  onTypes(
    "com.datastax.oss.driver.internal.core.cql.CqlPrepareHandler",
    "com.datastax.oss.driver.internal.core.cql.CqlRequestHandler"
  )
    .mixin(classOf[HasContext.Mixin])
    .advise(isConstructor(), classOf[OnRequestHandlerConstructorAdvice])
    .advise(method("onThrottleReady"), classOf[OnThrottleReadyAdvice])

}

object DriverInstrumentation {
  trait ClusterManagerBridge {
    @FieldBridge("clusterName")
    def getClusterName: String
  }
}

class OnRequestHandlerConstructorAdvice
object OnRequestHandlerConstructorAdvice {

  @Advice.OnMethodExit()
  @static def exit(@Advice.This requestHandler: HasContext, @Advice.Argument(0) req: Any): Unit = {
    val (operationName, statement) = req match {
      case pr: PrepareRequest      => (QueryOperations.QueryPrepareOperationName, pr.getQuery())
      case ss: cql.SimpleStatement => (QueryOperations.QueryOperationName, ss.getQuery())
      case bs: cql.BoundStatement  => (QueryOperations.QueryOperationName, bs.getPreparedStatement.getQuery())
      case bs: cql.BatchStatement  => (QueryOperations.BatchOperationName, "")
    }

    // Make that every case added to the "onTypes" clause for the Cassandra 4.x support
    // is also handled in this match.
    val resultStage: CompletionStage[_] = requestHandler match {
      case cph: CqlPrepareHandler => cph.handle()
      case crh: CqlRequestHandler => crh.handle()
    }

    val clientSpan = Kamon.clientSpanBuilder(operationName, "cassandra.driver")
      .tag("db.type", "cassandra")
      .tag("db.statement", statement)
      .start()

    /**
      * We are registering a callback on the result CompletionStage because the setFinalResult and
      * setFinalError methods might be called more than once on the same request handler.
      */
    resultStage.whenComplete(new BiConsumer[Any, Throwable] {
      override def accept(result: Any, error: Throwable): Unit = {
        if (error != null) {
          clientSpan
            .fail(error)
            .finish()
        } else {
          clientSpan.finish()
        }
      }
    })
  }
}

class OnThrottleReadyAdvice
object OnThrottleReadyAdvice {

  @Advice.OnMethodEnter()
  @static def enter(@Advice.This requestHandler: HasContext): Unit = {
    val querySpan = requestHandler.context.get(Span.Key)
    querySpan.mark("cassandra.throttle.ready")
  }
}
