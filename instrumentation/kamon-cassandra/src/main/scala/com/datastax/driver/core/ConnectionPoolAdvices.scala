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

import java.util.concurrent.atomic.AtomicInteger
import com.google.common.util.concurrent.{FutureCallback, ListenableFuture}
import kamon.Kamon
import kamon.instrumentation.cassandra.metrics.{HasPoolMetrics, NodeMonitor}
import kamon.instrumentation.cassandra.CassandraInstrumentation
import kamon.util.CallingThreadExecutionContext
import kanela.agent.libs.net.bytebuddy.asm.Advice

import scala.annotation.static

class PoolConstructorAdvice
object PoolConstructorAdvice {

  @Advice.OnMethodExit
  @static def onConstructed(
      @Advice.This poolWithMetrics:                      HostConnectionPool with HasPoolMetrics,
      @Advice.FieldValue("host") host:                   Host,
      @Advice.FieldValue("totalInFlight") totalInflight: AtomicInteger
  ): Unit = {
    val node             = CassandraInstrumentation.createNode(host)
    val samplingInterval = CassandraInstrumentation.settings.sampleInterval

    poolWithMetrics.setNodeMonitor(new NodeMonitor(node))

    if(poolWithMetrics.nodeMonitor.poolMetricsEnabled) {
      poolWithMetrics.nodeMonitor.poolMetrics.inFlight.autoUpdate(inFlightTracker => {
        inFlightTracker.record(totalInflight.longValue())
        ()
      }, samplingInterval)
    }
  }
}

class PoolCloseAdvice
object PoolCloseAdvice {

  @Advice.OnMethodExit
  @static def onClose(@Advice.This poolWithMetrics: HostConnectionPool with HasPoolMetrics): Unit = {
    poolWithMetrics.nodeMonitor.cleanup()
  }
}

/**
  * Measure time spent waiting for a connection
  * Record number of in-flight queries on just-acquired connection
  */
class BorrowAdvice
object BorrowAdvice {

  @Advice.OnMethodEnter
  @static def startBorrow(@Advice.This poolMetrics: HasPoolMetrics): Long = {
    Kamon.clock().nanos()
  }

  @Advice.OnMethodExit(suppress = classOf[Throwable], inline = false)
  @static def onBorrowed(
      @Advice.Return connection:  ListenableFuture[Connection],
      @Advice.Enter start:        Long,
      @Advice.This poolMetrics:   HasPoolMetrics,
      @Advice.FieldValue("totalInFlight") totalInflight: AtomicInteger
  ): Unit = {

    GuavaCompatibility.INSTANCE.addCallback(
      connection,
      new FutureCallback[Connection]() {
        override def onSuccess(borrowedConnection: Connection): Unit = {
          poolMetrics.nodeMonitor.recordBorrow(Kamon.clock().nanos() - start)
        }
        override def onFailure(t: Throwable): Unit = ()
      }
    )
  }
}

/**
  * Track number of active connections towards the given host
  * Incremented when new connection requested and decremented either on
  * connection being explicitly trashed or defunct
  */
class InitPoolAdvice
object InitPoolAdvice {

  @Advice.OnMethodExit
  @static def onPoolInited(
      @Advice.This hasPoolMetrics:                HasPoolMetrics,
      @Advice.Return done:                        ListenableFuture[_],
      @Advice.FieldValue("open") openConnections: AtomicInteger
  ): Unit = {

    done.addListener(new Runnable {
      override def run(): Unit = {
        hasPoolMetrics.nodeMonitor.connectionsOpened(openConnections.get())
      }
    }, CallingThreadExecutionContext)
  }
}

class CreateConnectionAdvice
object CreateConnectionAdvice {

  @Advice.OnMethodExit
  @static def onConnectionCreated(
      @Advice.This hasPoolMetrics: HasPoolMetrics,
      @Advice.Return created:      Boolean
  ): Unit =
    if (created) {
      hasPoolMetrics.nodeMonitor.connectionsOpened(1)
    }
}

class TrashConnectionAdvice
object TrashConnectionAdvice {

  @Advice.OnMethodExit
  @static def onConnectionTrashed(
      @Advice.This hasPoolMetrics:     HasPoolMetrics,
      @Advice.FieldValue("host") host: Host
  ): Unit = {
    hasPoolMetrics.nodeMonitor.connectionTrashed
  }
}

class ConnectionDefunctAdvice
object ConnectionDefunctAdvice {

  @Advice.OnMethodExit
  @static def onConnectionDefunct(@Advice.This hasPoolMetrics: HasPoolMetrics): Unit = {
    hasPoolMetrics.nodeMonitor.connectionClosed
  }
}
