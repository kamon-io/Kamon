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
import com.datastax.driver.core.{QueryOperations, Session}
import com.datastax.driver.core.querybuilder.QueryBuilder
import kamon.module.Module.Registration
import kamon.testkit.{InstrumentInspection, MetricInspection, Reconfigure, TestSpanReporter}
import org.cassandraunit.utils.EmbeddedCassandraServerHelper
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar
import org.scalatest.{BeforeAndAfterAll, Matchers, OptionValues, WordSpec}

import scala.collection.JavaConverters._
import kamon.tag.Lookups._

class CassandraClientTracingInstrumentationSpec
    extends WordSpec
    with Matchers
    with Eventually
    with SpanSugar
    with BeforeAndAfterAll
    with MetricInspection.Syntax
    with InstrumentInspection.Syntax
    with Reconfigure
    with OptionValues
    with TestSpanReporter {

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
      testSpanReporter().clear()
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

    "not swallow exceptions" in {
      testSpanReporter().clear()

      val query = QueryBuilder
        .select("name")
        .from("illegaltablename")
        .where(QueryBuilder.eq("name", "kamon"))
        .allowFiltering()
        .setFetchSize(5)

      assertThrows[DriverException] {
        session.execute(query)
      }
    }

    "trace individual page executions" in {
      testSpanReporter().clear()

      val query = QueryBuilder
        .select("name")
        .from("users")
        .where(QueryBuilder.eq("name", "kamon"))
        .allowFiltering()
        .setFetchSize(5)

      session.execute(query).iterator().asScala.foreach(_ => ())

      eventually(timeout(10 seconds)) {
        val spans          = testSpanReporter().spans()
        val clientSpan     = spans.find(_.operationName == QueryOperations.QueryOperationName)
        val executionSpans = spans.filter(_.operationName == QueryOperations.ExecutionOperationName)

        clientSpan should not be empty
        executionSpans.size should equal(3)

        clientSpan.get.tags.get(plainLong("cassandra.driver.rs.fetch-size")) should equal(5L)
        clientSpan.get.tags.get(plainLong("cassandra.driver.rs.fetched")) should equal(5L)
        clientSpan.get.tags.get(plainBoolean("cassandra.driver.rs.has-more")) shouldBe true
      }
    }
  }

  var registration: Registration = _
  var session:      Session      = _

  override protected def beforeAll(): Unit = {
    enableFastSpanFlushing()
    sampleAlways()

    val keyspace = s"keyspaceTracingSpec"

    EmbeddedCassandraServerHelper.startEmbeddedCassandra(40000L)
    session = EmbeddedCassandraServerHelper.getCluster.newSession()

    session.execute(
      s"create keyspace $keyspace with replication = {'class':'SimpleStrategy', 'replication_factor':3}"
    )

    session.execute(s"USE $keyspace")

    session.execute("create table users (id uuid primary key, name text )")
    for (i <- 1 to 12) {
      session.execute("insert into users (id, name) values (uuid(), 'kamon')")
    }
  }

}
