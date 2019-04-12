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

import java.sql.{DriverManager, ResultSet}
import java.util.concurrent.Executors

import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.context.Context
import kamon.jdbc.Metrics
import kamon.jdbc.instrumentation.StatementMonitor.StatementTypes
import kamon.module.Module.Registration
import kamon.testkit.{InstrumentInspection, MetricInspection, Reconfigure, TestSpanReporter}
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar
import org.scalatest.{BeforeAndAfterAll, Matchers, OptionValues, WordSpec}
import kamon.tag.Lookups._
import kamon.trace.SpanBuilder
import kamon.trace.Tracer.PreStartHook

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class Ba extends PreStartHook {
  override def beforeStart(builder: SpanBuilder): Unit = {

  }
}

class StatementInstrumentationSpec extends WordSpec with Matchers with Eventually with SpanSugar with BeforeAndAfterAll
    with MetricInspection.Syntax with InstrumentInspection.Syntax with Reconfigure with OptionValues {

  implicit val parallelQueriesExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10))

  Kamon.reconfigure(ConfigFactory.parseString(
    """
      |kamon.trace.hooks.pre-start = [ "kamon.trace.Hooks$PreStart$FromContext" ]
    """.stripMargin).withFallback(Kamon.config()))

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
        Metrics.Statements.inFlight.withoutTags().distribution().max shouldBe 10
      }

      eventually(timeout(2 seconds)) {
        Metrics.Statements.inFlight.withoutTags().distribution().max shouldBe 0
      }

      reporter.clear()
    }

    "generate Spans on calls to .execute() in prepared statements" in {
      val select = s"SELECT * FROM Address where Nr = 1"
      val statement = connection.prepareStatement(select)
      statement.execute()
      validateNextRow(statement.getResultSet, valueNr = 1, valueName = "foo")

      eventually {
        val span = reporter.nextSpan().value
        span.operationName shouldBe StatementTypes.GenericExecute
        span.tags.get(plain("component")) shouldBe "jdbc"
        span.tags.get(plain("db.statement")) should include("SELECT * FROM Address where Nr = 1")
        reporter.nextSpan() shouldBe None
      }
    }

    "generate Spans on calls to .execute(sql) in statements" in {
      val select = s"SELECT * FROM Address where Nr = 2"
      val statement = connection.createStatement()
      statement.execute(select)
      validateNextRow(statement.getResultSet, valueNr = 2, valueName = "foo")


      eventually {
        val span = reporter.nextSpan().value
        span.operationName shouldBe StatementTypes.GenericExecute
        span.tags.get(plain("component")) shouldBe "jdbc"
        span.tags.get(plain("db.statement")) should include("SELECT * FROM Address where Nr = 2")
        reporter.nextSpan() shouldBe None
      }
    }

    "generate Spans on calls to .executeQuery() in prepared statements" in {
      val select = s"SELECT * FROM Address where Nr = 3"
      val statement = connection.prepareStatement(select)
      statement.executeQuery()
      validateNextRow(statement.getResultSet, valueNr = 3, valueName = "foo")

      eventually {
        val span = reporter.nextSpan().value
        span.operationName shouldBe StatementTypes.Query
        span.tags.get(plain("component")) shouldBe "jdbc"
        span.tags.get(plain("db.statement")) should include("SELECT * FROM Address where Nr = 3")
        reporter.nextSpan() shouldBe None
      }
    }

    "generate Spans on calls to .executeQuery(sql) in statements" in {
      val select = s"SELECT * FROM Address where Nr = 4"
      val statement = connection.createStatement()
      statement.executeQuery(select)
      validateNextRow(statement.getResultSet, valueNr = 4, valueName = "foo")

      eventually {
        val span = reporter.nextSpan().value
        span.operationName shouldBe StatementTypes.Query
        span.tags.get(plain("component")) shouldBe "jdbc"
        span.tags.get(plain("db.statement")) should include("SELECT * FROM Address where Nr = 4")
        reporter.nextSpan() shouldBe None
      }
    }

    "generate Spans on calls to .executeUpdate() in prepared statements" in {
      val insert = s"INSERT INTO Address (Nr, Name) VALUES(5, 'foo')"
      val affectedRows = connection.prepareStatement(insert).executeUpdate()
      affectedRows shouldBe 1

      eventually {
        val span = reporter.nextSpan().value
        span.operationName shouldBe StatementTypes.Update
        span.tags.get(plain("component")) shouldBe "jdbc"
        span.tags.get(plain("db.statement")) should include("INSERT INTO Address (Nr, Name) VALUES(5, 'foo')")
        reporter.nextSpan() shouldBe None
      }
    }

    "generate Spans on calls to .executeUpdate(sql) in statements" in {
      val insert = s"INSERT INTO Address (Nr, Name) VALUES(6, 'foo')"
      val affectedRows = connection.createStatement().executeUpdate(insert)
      affectedRows shouldBe 1

      eventually {
        val span = reporter.nextSpan().value
        span.operationName shouldBe StatementTypes.Update
        span.tags.get(plain("component")) shouldBe "jdbc"
        span.tags.get(plain("db.statement")) should include("INSERT INTO Address (Nr, Name) VALUES(6, 'foo')")
        reporter.nextSpan() shouldBe None
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
        span.tags.get(plain("component")) shouldBe "jdbc"
        span.tags.get(plain("db.statement")).toString should include("INSERT INTO Address (Nr, Name) VALUES(?, 'foo')")
        reporter.nextSpan() shouldBe None
      }
    }

    //TODO should be covered by kamon-core tests
/*    "pickup a SpanCustomizer from the current context and apply it to the new spans" in {
      val select = s"SELECT * FROM Address where Nr = 1"
      connection.prepareStatement(select).executeQuery()

      eventually {
        val span = reporter.nextSpan().value
        span.operationName shouldBe "getAddress"
        span.tags.get(plain("component")) shouldBe "jdbc"
        span.tags.get(plain("db.statement")) should include("SELECT * FROM Address where Nr = 1")
        reporter.nextSpan() shouldBe None
      }
    }*/

    "add errors to Spans when errors happen" in {
      val insert = s"INSERT INTO NotATable (Nr, Name) VALUES(1, 'foo')"
      val select = s"SELECT * FROM NotATable where Nr = 1"

      Try(connection.createStatement().execute(select))

      eventually {
        val span = reporter.nextSpan().value
        span.operationName shouldBe StatementTypes.GenericExecute
        span.tags.get(plain("component")) shouldBe "jdbc"
        span.metricTags.get(plainBoolean("error")) shouldBe (true)
        span.tags.get(option("error.message")) should be ('defined)
        span.tags.get(option("error.stacktrace")) should be ('defined)
        reporter.nextSpan() shouldBe None
      }

      Try(connection.createStatement().executeQuery(select))

      eventually {
        val span = reporter.nextSpan().value
        span.operationName shouldBe StatementTypes.Query
        span.tags.get(plain("component")) shouldBe "jdbc"
        span.metricTags.get(plainBoolean("error")) shouldBe (true)
        span.tags.get(option("error.message")) should be ('defined)
        span.tags.get(option("error.stacktrace")) should be ('defined)
        reporter.nextSpan() shouldBe None
      }

      Try(connection.createStatement().executeUpdate(insert))

      eventually {
        val span = reporter.nextSpan().value
        span.operationName shouldBe StatementTypes.Update
        span.tags.get(plain("component")) shouldBe "jdbc"
        span.metricTags.get(plainBoolean("error")) shouldBe (true)
      }
    }

    "rethrow the exception when error happen" in {
      val select = s"SELECT * FROM NotATable where Nr = 1"

      Try(connection.createStatement().execute(select)).failed.get.getMessage should include("""Table "NOTATABLE" not found""")
    }
  }

  private def validateNextRow(resultSet: ResultSet, valueNr: Int, valueName: String, shouldBeMore: Boolean = false): Unit = {
    resultSet.next() shouldBe true
    resultSet.getInt("Nr") shouldBe valueNr
    resultSet.getString("Name") shouldBe valueName
    resultSet.next() shouldBe shouldBeMore
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

    connection.createStatement().executeUpdate(s"INSERT INTO Address (Nr, Name) VALUES(1, 'foo')")
    connection.createStatement().executeUpdate(s"INSERT INTO Address (Nr, Name) VALUES(2, 'foo')")
    connection.createStatement().executeUpdate(s"INSERT INTO Address (Nr, Name) VALUES(3, 'foo')")
    connection.createStatement().executeUpdate(s"INSERT INTO Address (Nr, Name) VALUES(4, 'foo')")

    Metrics.Statements.inFlight.withoutTags().distribution()

    enableFastSpanFlushing()
    sampleAlways()
    registration = Kamon.registerModule("testReporter", reporter)
  }

  override protected def afterAll(): Unit = {
    registration.cancel()
  }
}