/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon.jdbc.instrumentation

import java.sql.DriverManager
import java.util.concurrent.Executors

import kamon.Kamon
import kamon.context.Context
import kamon.jdbc.instrumentation.StatementInstrumentation.StatementTypes
import kamon.jdbc.Metrics
import kamon.testkit.{MetricInspection, Reconfigure, TestSpanReporter}
import kamon.trace.Span.TagValue
import kamon.trace.SpanCustomizer
import kamon.util.Registration
import org.scalatest.{BeforeAndAfterAll, Matchers, OptionValues, WordSpec}
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class StatementInstrumentationSpec extends WordSpec with Matchers with Eventually with SpanSugar with BeforeAndAfterAll
    with MetricInspection with Reconfigure with OptionValues {

  implicit val parallelQueriesExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10))

  "the StatementInstrumentation" should {
    "track in-flight operations" in {
      for (_ ← 1 to 10) yield {
        Future {
          DriverManager
            .getConnection("jdbc:h2:mem:jdbc-spec", "SA", "")
            .prepareStatement(s"SELECT 1; CALL SLEEP(500)")
            .execute()
        }
      }

      eventually(timeout(2 seconds)) {
        Metrics.Statements.InFlight.refine().distribution().max shouldBe 10
      }

      eventually(timeout(2 seconds)) {
        Metrics.Statements.InFlight.refine().distribution().max shouldBe 0
      }

      reporter.clear()
    }

    "generate Spans on calls to .execute() in prepared statements" in {
      val select = s"SELECT * FROM Address where Nr = 1"
      connection.prepareStatement(select).execute()

      eventually {
        val span = reporter.nextSpan().value
        span.operationName shouldBe StatementTypes.GenericExecute
        span.tags("component") shouldBe TagValue.String("jdbc")
        span.tags("db.statement").toString should include("SELECT * FROM Address where Nr = 1")
      }
    }

    "generate Spans on calls to .execute(sql) in statements" in {
      val select = s"SELECT * FROM Address where Nr = 2"
      connection.createStatement().execute(select)

      eventually {
        val span = reporter.nextSpan().value
        span.operationName shouldBe StatementTypes.GenericExecute
        span.tags("component") shouldBe TagValue.String("jdbc")
        span.tags("db.statement").toString should include("SELECT * FROM Address where Nr = 2")
      }
    }

    "generate Spans on calls to .executeQuery() in prepared statements" in {
      val select = s"SELECT * FROM Address where Nr = 3"
      connection.prepareStatement(select).executeQuery()

      eventually {
        val span = reporter.nextSpan().value
        span.operationName shouldBe StatementTypes.Query
        span.tags("component") shouldBe TagValue.String("jdbc")
        span.tags("db.statement").toString should include("SELECT * FROM Address where Nr = 3")
      }
    }

    "generate Spans on calls to .executeQuery(sql) in statements" in {
      val select = s"SELECT * FROM Address where Nr = 4"
      connection.createStatement().executeQuery(select)

      eventually {
        val span = reporter.nextSpan().value
        span.operationName shouldBe StatementTypes.Query
        span.tags("component") shouldBe TagValue.String("jdbc")
        span.tags("db.statement").toString should include("SELECT * FROM Address where Nr = 4")
      }
    }

    "generate Spans on calls to .executeUpdate() in prepared statements" in {
      val insert = s"INSERT INTO Address (Nr, Name) VALUES(1, 'foo')"
      connection.prepareStatement(insert).executeUpdate()

      eventually {
        val span = reporter.nextSpan().value
        span.operationName shouldBe StatementTypes.Update
        span.tags("component") shouldBe TagValue.String("jdbc")
        span.tags("db.statement").toString should include("INSERT INTO Address (Nr, Name) VALUES(1, 'foo')")
      }
    }

    "generate Spans on calls to .executeUpdate(sql) in statements" in {
      val insert = s"INSERT INTO Address (Nr, Name) VALUES(2, 'foo')"
      connection.createStatement().executeUpdate(insert)

      eventually {
        val span = reporter.nextSpan().value
        span.operationName shouldBe StatementTypes.Update
        span.tags("component") shouldBe TagValue.String("jdbc")
        span.tags("db.statement").toString should include("INSERT INTO Address (Nr, Name) VALUES(2, 'foo')")
      }
    }

    "generate Spans on calls to .executeBatch() in prepared statements" in {
      val statement = connection.prepareStatement("INSERT INTO Address (Nr, Name) VALUES(?, 'foo')")
      statement.setInt(1, 1)
      statement.addBatch()

      statement.setInt(1, 2)
      statement.addBatch()
      statement.executeBatch()


      eventually {
        val span = reporter.nextSpan().value
        span.operationName shouldBe StatementTypes.Batch
        span.tags("component") shouldBe TagValue.String("jdbc")
        span.tags("db.statement").toString should include("INSERT INTO Address (Nr, Name) VALUES(?, 'foo')")
      }
    }

    "pickup a SpanCustomizer from the current context and apply it to the new spans" in {
      val select = s"SELECT * FROM Address where Nr = 1"
      Kamon.withContext(Context(SpanCustomizer.ContextKey, SpanCustomizer.forOperationName("getAddress"))) {
        connection.prepareStatement(select).executeQuery()
      }

      eventually {
        val span = reporter.nextSpan().value
        span.operationName shouldBe "getAddress"
        span.tags("component") shouldBe TagValue.String("jdbc")
        span.tags("db.statement").toString should include("SELECT * FROM Address where Nr = 1")
      }
    }

    "add errors to Spans when errors happen" in {
      val insert = s"INSERT INTO NotATable (Nr, Name) VALUES(1, 'foo')"
      val select = s"SELECT * FROM NotATable where Nr = 1"

      Try(connection.createStatement().execute(select))

      eventually {
        val span = reporter.nextSpan().value
        span.operationName shouldBe StatementTypes.GenericExecute
        span.tags("component") shouldBe TagValue.String("jdbc")
        span.tags("error") shouldBe TagValue.True
      }

      Try(connection.createStatement().executeQuery(select))

      eventually {
        val span = reporter.nextSpan().value
        span.operationName shouldBe StatementTypes.Query
        span.tags("component") shouldBe TagValue.String("jdbc")
        span.tags("error") shouldBe TagValue.True
      }

      Try(connection.createStatement().executeUpdate(insert))

      eventually {
        val span = reporter.nextSpan().value
        span.operationName shouldBe StatementTypes.Update
        span.tags("component") shouldBe TagValue.String("jdbc")
        span.tags("error") shouldBe TagValue.True
      }
    }
  }

  var registration: Registration = _
  val connection = DriverManager.getConnection("jdbc:h2:mem:jdbc-spec;MULTI_THREADED=1", "SA", "")
  val reporter = new TestSpanReporter()

  override protected def beforeAll(): Unit = {
    connection
      .prepareStatement("CREATE TABLE Address (Nr INTEGER, Name VARCHAR(128));")
      .executeUpdate()

    connection
      .prepareStatement("CREATE ALIAS SLEEP FOR \"java.lang.Thread.sleep(long)\"")
      .executeUpdate()

    Metrics.Statements.InFlight.refine().distribution()

    enableFastSpanFlushing()
    sampleAlways()
    registration = Kamon.addReporter(reporter)
  }

  override protected def afterAll(): Unit = {
    registration.cancel()
  }
}