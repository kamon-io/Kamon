/*
 *  ==========================================================================================
 *  Copyright © 2013-2020 The Kamon Project <https://kamon.io/>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 *  except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the
 *  License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied. See the License for the specific language governing permissions
 *  and limitations under the License.
 *  ==========================================================================================
 */

package kamon
package instrumentation
package cassandra
package driver

import com.datastax.driver.core._
import kamon.instrumentation.cassandra.driver.DriverInstrumentation.ClusterManagerBridge
import kamon.instrumentation.cassandra.metrics.HasPoolMetrics
import kamon.instrumentation.context.HasContext.MixinWithInitializer
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.api.instrumentation.bridge.FieldBridge

class DriverInstrumentation extends InstrumentationBuilder {

  /**
    * Wraps the client session with an InstrumentedSession.
    */
  onType("com.datastax.driver.core.Cluster$Manager")
    .intercept(method("newSession"), SessionInterceptor)
    .bridge(classOf[ClusterManagerBridge])

  /**
    * Instrument  connection pools (one per target host)
    * Pool size is incremented on pool init and when new connections are added
    * and decremented when connection is deemed defunct or explicitly trashed.
    * Pool metrics are mixed in the pool object itself
    */
  onType("com.datastax.driver.core.HostConnectionPool")
    .advise(method("borrowConnection"), BorrowAdvice)
    .advise(method("trashConnection"), TrashConnectionAdvice)
    .advise(method("addConnectionIfUnderMaximum"), CreateConnectionAdvice)
    .advise(method("onConnectionDefunct"), ConnectionDefunctAdvice)
    .advise(isConstructor, PoolConstructorAdvice)
    .advise(method("initAsync"), InitPoolAdvice)
    .advise(method("closeAsync"), PoolCloseAdvice)
    .mixin(classOf[HasPoolMetrics.Mixin])

  /**
    * Trace each query sub-execution as a child of client query,
    * this includes retries, speculative executions and fetchMore executions.
    * Once response is ready (onSet), context is carried via Message.Response mixin
    * to be used for further fetches
    */
  onType("com.datastax.driver.core.RequestHandler$SpeculativeExecution")
    .advise(method("query"), QueryExecutionAdvice)
    .advise(method("write"), QueryWriteAdvice)
    .advise(method("onException"), OnExceptionAdvice)
    .advise(method("onTimeout"), OnTimeoutAdvice)
    .advise(method("onSet"), OnSetAdvice)
    .mixin(classOf[MixinWithInitializer])

  onSubTypesOf("com.datastax.driver.core.Message$Response")
    .mixin(classOf[MixinWithInitializer])

  onType("com.datastax.driver.core.ArrayBackedResultSet")
    .advise(method("fromMessage"), OnResultSetConstruction)

  /**
    * In order for fetchMore execution to be a sibling of original execution
    * we need to carry parent-span id through result sets
    */
  onType("com.datastax.driver.core.ArrayBackedResultSet$MultiPage")
    .mixin(classOf[MixinWithInitializer])
  onType("com.datastax.driver.core.ArrayBackedResultSet$MultiPage")
    .advise(method("queryNextPage"), OnFetchMore)

  /**
    * Query metrics are tagged with target information (based on config)
    * so all query metrics are mixed into a Host object
    */
  onType("com.datastax.driver.core.Host")
    .mixin(classOf[HasPoolMetrics.Mixin])
    .advise(method("setLocationInfo"), HostLocationAdvice)

}

object DriverInstrumentation {
  trait ClusterManagerBridge {
    @FieldBridge("clusterName")
    def getClusterName: String
  }
}
