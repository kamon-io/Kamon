/* =========================================================================================
 * Copyright © 2013-2021 the kamon project <http://kamon.io/>
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


import com.datastax.driver.core.QueryOperations
import com.datastax.oss.driver.api.core.servererrors.SyntaxError
import com.datastax.oss.driver.api.core.{CqlSession, DriverException}
import kamon.testkit.{InitAndStopKamonAfterAll, InstrumentInspection, MetricInspection, Reconfigure, TestSpanReporter}
import kamon.trace.Span
import org.scalactic.TimesOnInt.convertIntToRepeater
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar
import org.scalatest.wordspec.AnyWordSpec
import org.testcontainers.containers.CassandraContainer

import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.ExecutionException

class CassandraClientTracingInstrumentationSpec
    extends AnyWordSpec
    with Matchers
    with Eventually
    with SpanSugar
    with InitAndStopKamonAfterAll
    with BeforeAndAfterEach
    with MetricInspection.Syntax
    with InstrumentInspection.Syntax
    with Reconfigure
    with OptionValues
    with TestSpanReporter {

  override protected def beforeEach(): Unit = testSpanReporter().clear()
  override protected def afterEach(): Unit = testSpanReporter().clear()

  "The Cassandra Driver 4.x instrumentation" should {

    "Trace prepare executions" in {
      session.prepare("SELECT * FROM users where name = 'kamon' ALLOW FILTERING")
      session.prepareAsync("SELECT * FROM users where name = 'telemetry' ALLOW FILTERING")
        .toCompletableFuture()
        .get()

      2 times {
        eventually(timeout(10 seconds)) {
          val span = testSpanReporter().nextSpan().value
          span.operationName shouldBe QueryOperations.QueryPrepareOperationName
          span.kind shouldBe Span.Kind.Client
        }
      }
    }

    "Trace failures in prepare executions" in {
      assertThrows[SyntaxError] {
        session.prepare("THIS IS NOT A VALID QUERY")
      }

      assertThrows[ExecutionException] {
        session.prepareAsync("AGAIN, THIS IS NOT A VALID QUERY")
          .toCompletableFuture()
          .get()
      }

      2 times {
        eventually(timeout(10 seconds)) {
          val span = testSpanReporter().nextSpan().value
          span.operationName shouldBe QueryOperations.QueryPrepareOperationName
          span.kind shouldBe Span.Kind.Client
          span.hasError shouldBe true
        }
      }
    }

    "Trace queries sent to Cassandra" in {
      session.execute("SELECT * FROM users where name = 'kamon' ALLOW FILTERING")
      session.executeAsync("SELECT * FROM users where name = 'telemetry' ALLOW FILTERING")
        .toCompletableFuture()
        .get()

      2 times {
        eventually(timeout(10 seconds)) {
          val span = testSpanReporter().nextSpan().value
          span.operationName shouldBe QueryOperations.QueryOperationName
          span.kind shouldBe Span.Kind.Client
        }
      }
    }
  }


  var session: CqlSession = _
  val cassandra = new CassandraContainer("cassandra:3.11.10")

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    enableFastSpanFlushing()
    sampleAlways()

    cassandra.withStartupTimeout(Duration.ofMinutes(5))
    cassandra.start()

    val keyspace = s"keyspaceTracingSpec"
    val cassandraHost = cassandra.getHost
    val cassandraPort = cassandra.getMappedPort(CassandraContainer.CQL_PORT)

    session = CqlSession
      .builder()
      .addContactPoint(InetSocketAddress.createUnresolved(cassandraHost, cassandraPort))
      .withLocalDatacenter("datacenter1")
      .build()

    session.execute(s"create keyspace $keyspace with replication = {'class':'SimpleStrategy', 'replication_factor':3}")
    session.execute(s"USE $keyspace")
    session.execute("create table users (id uuid primary key, name text )")

    for (_ <- 1 to 12) {
      session.execute("insert into users (id, name) values (uuid(), 'kamon')")
    }
  }

  override protected def afterAll(): Unit = {
    session.close()
    cassandra.stop()
    super.afterAll()
  }
}
