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

import com.datastax.driver.core.exceptions.DriverException
import com.datastax.driver.core.querybuilder.QueryBuilder
import com.datastax.driver.core.{QueryOperations, Session, SimpleStatement}
import kamon.tag.Lookups._
import kamon.testkit.{InitAndStopKamonAfterAll, InstrumentInspection, MetricInspection, Reconfigure, TestSpanReporter}
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.testcontainers.containers.CassandraContainer

import java.time.Duration
import scala.collection.JavaConverters._

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

  "the CassandraClientTracingInstrumentation" should {

    "trace query prepare" in {
      session.prepare(
        "SELECT * FROM users where name = 'kamon' ALLOW FILTERING"
      )
      eventually(timeout(10 seconds)) {
        testSpanReporter().nextSpan().map(_.operationName) shouldBe Some(
          QueryOperations.QueryPrepareOperationName
        )
      }
    }

    "trace execution" in {
      session.execute(
        "SELECT * FROM users where name = 'kamon' ALLOW FILTERING"
      )
      eventually(timeout(10 seconds)) {
        val spans         = testSpanReporter().spans()
        val clientSpan    = spans.find(_.operationName == QueryOperations.QueryOperationName)
        val executionSpan = spans.find(_.operationName == QueryOperations.ExecutionOperationName)

        clientSpan should not be empty
        executionSpan should not be empty

        executionSpan.get.parentId should equal(clientSpan.get.id)
        executionSpan.get.marks
          .find(_.key == "cassandra.connection.write-started") should not be empty
      }
    }

    "trace individual page executions" in {
      val query = QueryBuilder
        .select("name")
        .from("users")
        .where(QueryBuilder.eq("name", "kamon"))
        .allowFiltering()
        .setFetchSize(5)

      session.execute(query).iterator().asScala.foreach(_ => ())

      eventually(timeout(20 seconds)) {
        val spans          = testSpanReporter().spans()
        val clientSpan     = spans.find(span => span.operationName == QueryOperations.QueryOperationName)
        val executionSpans = spans.filter(span => span.operationName == QueryOperations.ExecutionOperationName)

        clientSpan should not be empty
        executionSpans.size should equal(3)

        clientSpan.get.tags.get(plainLong("cassandra.driver.rs.fetch-size")) should equal(5L)
        clientSpan.get.tags.get(plainLong("cassandra.driver.rs.fetched")) should equal(5L)
        clientSpan.get.tags.get(plainBoolean("cassandra.driver.rs.has-more")) shouldBe true
      }
    }

    "not swallow exceptions" in {
      val query = QueryBuilder
        .select("name")
        .from("illegaltablename")
        .where(QueryBuilder.eq("name", "kamon"))
        .allowFiltering()
        .setFetchSize(5)

      assertThrows[DriverException] {
        session.execute(query)
      }

      eventually(timeout(10 seconds)) {
        val span = testSpanReporter().nextSpan()
        span should not be empty
      }
    }
  }

  var session: Session = _
  val cassandra = new CassandraContainer("cassandra:3.11.10")

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    enableFastSpanFlushing()
    sampleAlways()

    val keyspace = s"keyspaceTracingSpec"

    cassandra.withStartupTimeout(Duration.ofMinutes(5))
    cassandra.start()
    session = cassandra.getCluster.newSession()

    session.execute(statement(s"create keyspace $keyspace with replication = {'class':'SimpleStrategy', 'replication_factor':3}"))
    session.execute(statement(s"USE $keyspace"))
    session.execute(statement("create table users (id uuid primary key, name text )"))

    for (_ <- 1 to 12) {
      session.execute("insert into users (id, name) values (uuid(), 'kamon')")
    }
  }

  def statement(query: String): SimpleStatement = {
    val stmt = new SimpleStatement(query)
    stmt.setReadTimeoutMillis(20000)
    stmt
  }

  override protected def afterAll(): Unit = {
    session.close()
    cassandra.stop()
    super.afterAll()
  }
}
