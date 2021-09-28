/* =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License") you may not use this file
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

package kamon.instrumentation.instrumentation

import com.datastax.driver.core.Session
import kamon.Kamon
import kamon.instrumentation.cassandra.CassandraInstrumentation.Node
import kamon.instrumentation.cassandra.NodeConnectionPoolMetrics
import kamon.instrumentation.cassandra.NodeConnectionPoolMetrics.NodeConnectionPoolInstruments
import kamon.instrumentation.executor.ExecutorMetrics
import kamon.tag.TagSet
import kamon.testkit.{InitAndStopKamonAfterAll, InstrumentInspection, MetricInspection}
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar
import org.scalatest.{Matchers, OptionValues, WordSpec}
import org.testcontainers.containers.CassandraContainer

class CassandraClientMetricsSpec
    extends WordSpec
    with Matchers
    with Eventually
    with SpanSugar
    with InitAndStopKamonAfterAll
    with MetricInspection.Syntax
    with InstrumentInspection.Syntax
    with OptionValues {

  "the CassandraClientMetrics" should {

    "track client metrics" in {
      val st = session
        .prepare(
          "SELECT * FROM users where name = 'kamon' ALLOW FILTERING"
        )
        .bind()
      for (_ <- 1 to 100) yield {
        session.execute(st)
      }

      // We are reading the only possible host from the metric tags to ensure that we lookup the
      // right instruments, regardless of whether localhost resolved to 127.0.0.1 or 0:0:0:0:0:0:0:1
      val hostAddress = NodeConnectionPoolMetrics.InFlight.tagValues("cassandra.node").head
      val node        = Node(hostAddress, "datacenter1", "rack1")
      val poolMetrics = new NodeConnectionPoolInstruments(node)

      eventually(timeout(20 seconds)) {
        poolMetrics.borrow.distribution(false).max shouldBe >=(1L)
        poolMetrics.openConnections.distribution(false).max should be > 0L
        poolMetrics.inFlight.distribution(false).max should be > 0L

        poolMetrics.clientErrors.value(true) should equal(0)
        poolMetrics.timeouts.value(true) should equal(0)
        poolMetrics.canceled.value(true) should equal(0)
      }

      val clientSpan = Kamon
        .timer("span.processing-time")
        .withTags(
          TagSet.from(
            Map(
              "component"            -> "cassandra.driver",
              "cassandra.query.kind" -> "select",
              "span.kind"            -> "client",
              "operation"            -> "cassandra.query",
              "error"                -> false
            )
          )
        )

      val executionSpan = Kamon
        .timer("span.processing-time")
        .withTags(
          TagSet.from(
            Map(
              "span.kind"         -> "client",
              "operation"         -> "cassandra.query.execution",
              "error"             -> false,
              "component"         -> "cassandra.driver"
            )
          )
        )

      executionSpan.distribution().max should be > 0L
      clientSpan.distribution().max should be > 0L
    }

    "track the cassandra client executors queue size" in {
      val stmt = session
        .prepare(
          "SELECT * FROM users where name = 'kamon' ALLOW FILTERING"
        )
        .bind()

      for (_ <- 1 to 10) yield {
        session.executeAsync(stmt)
      }

      eventually(timeout(10 seconds)) {
        val all = ExecutorMetrics.ThreadsTotal.instruments()
        all.map(_._2.distribution(false).max).forall(_ > 0) === true
      }
    }

  }

  var session: Session = _
  val cassandra = new CassandraContainer("cassandra:3.11.10")


  override protected def beforeAll(): Unit = {
    super.beforeAll()
    cassandra.start()
    session = cassandra.getCluster.newSession()
    val keyspace = s"keyspaceMetricSpec"

    session.execute(
      s"create keyspace $keyspace with replication = {'class':'SimpleStrategy', 'replication_factor':3}"
    )
    session.execute(s"USE $keyspace")
    session.execute("create table users (id uuid primary key, name text )")
    session.execute("insert into users (id, name) values (uuid(), 'kamon')")
  }

  override protected def afterAll(): Unit = {
    session.close()
    cassandra.stop()
    super.afterAll()
  }
}
