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

package com.datastax.driver.core

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import com.google.common.util.concurrent.{FutureCallback, ListenableFuture}
import kamon.Kamon
import kamon.instrumentation.cassandra.metrics.{HasPoolMetrics, NodeMonitor}
import kamon.instrumentation.cassandra.CassandraInstrumentation
import kamon.metric.Timer
import kanela.agent.libs.net.bytebuddy.asm.Advice

object PoolConstructorAdvice {

  @Advice.OnMethodExit
  def onConstructed(
      @Advice.This poolWithMetrics:                      HostConnectionPool with HasPoolMetrics,
      @Advice.FieldValue("host") host:                   Host,
      @Advice.FieldValue("totalInFlight") totalInflight: AtomicInteger
  ): Unit = {
    val clusterName      = poolWithMetrics.manager.getCluster.getClusterName
    val node             = CassandraInstrumentation.createNode(host, clusterName)
    val samplingInterval = CassandraInstrumentation.settings.sampleInterval.toMillis

    poolWithMetrics.setNodeMonitor(new NodeMonitor(node))

    val samplingSchedule = Kamon
      .scheduler()
      .scheduleAtFixedRate(
        new Runnable {
          override def run(): Unit = {
            poolWithMetrics.nodeMonitor.recordInFlightSample(totalInflight.longValue())
          }
        },
        samplingInterval,
        samplingInterval,
        TimeUnit.MILLISECONDS
      )

    poolWithMetrics.setSampling(samplingSchedule)
  }
}

object PoolCloseAdvice {

  @Advice.OnMethodExit
  def onClose(@Advice.This poolWithMetrics: HostConnectionPool with HasPoolMetrics): Unit = {
    Option(poolWithMetrics.getSampling).foreach(_.cancel(true))
  }
}

/**
  * Measure time spent waiting for a connection
  * Record number of in-flight queries on just-acquired connection
  */
object BorrowAdvice {

  @Advice.OnMethodEnter
  def startBorrow(@Advice.This poolMetrics: HasPoolMetrics): Long = {
    Kamon.clock().nanos()
  }

  @Advice.OnMethodExit(suppress = classOf[Throwable])
  def onBorrowed(
      @Advice.Return(readOnly = false) connection: ListenableFuture[Connection],
      @Advice.Enter start:                               Long,
      @Advice.This poolMetrics:                          HasPoolMetrics,
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
object InitPoolAdvice {

  @Advice.OnMethodExit
  def onPoolInited(
      @Advice.This hasPoolMetrics:                HasPoolMetrics,
      @Advice.Return done:                        ListenableFuture[_],
      @Advice.FieldValue("open") openConnections: AtomicInteger
  ): Unit = {

    done.addListener(new Runnable {
      override def run(): Unit = {
        hasPoolMetrics.nodeMonitor.connectionsOpened(openConnections.get())
      }
    }, Kamon.scheduler())
  }
}

object CreateConnectionAdvice {

  @Advice.OnMethodExit
  def onConnectionCreated(
      @Advice.This hasPoolMetrics: HasPoolMetrics,
      @Advice.Return created:      Boolean
  ): Unit =
    if (created) {
      hasPoolMetrics.nodeMonitor.connectionsOpened(1)
    }
}

object TrashConnectionAdvice {

  @Advice.OnMethodExit
  def onConnectionTrashed(
      @Advice.This hasPoolMetrics:     HasPoolMetrics,
      @Advice.FieldValue("host") host: Host
  ): Unit = {
    hasPoolMetrics.nodeMonitor.connectionTrashed
  }
}

object ConnectionDefunctAdvice {

  @Advice.OnMethodExit
  def onConnectionDefunct(@Advice.This hasPoolMetrics: HasPoolMetrics): Unit = {
    hasPoolMetrics.nodeMonitor.connectionClosed
  }
}
