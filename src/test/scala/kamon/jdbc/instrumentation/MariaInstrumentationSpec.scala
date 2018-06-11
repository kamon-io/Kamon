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

import java.sql.{Connection, DriverManager, ResultSet}

import ch.vorburger.mariadb4j.DB
import kamon.Kamon
import kamon.jdbc.Metrics
import kamon.jdbc.instrumentation.StatementInstrumentation.StatementTypes
import kamon.testkit.{MetricInspection, Reconfigure, TestSpanReporter}
import kamon.trace.Span.TagValue
import kamon.util.Registration
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar
import org.scalatest.{BeforeAndAfterAll, Matchers, OptionValues, WordSpec}

class MariaInstrumentationSpec extends WordSpec with Matchers with Eventually with SpanSugar with BeforeAndAfterAll
  with MetricInspection with Reconfigure with OptionValues {

  "the MariaInstrumentation" should {
    "generate Spans on calls to .executeQuery() in prepared statements" in {
      val select = s"SELECT * FROM Address where Nr = 3"
      val resultSet = connection.prepareStatement(select).executeQuery()
      validateNextRow(resultSet, valueNr = 3, valueName = "foo")

      eventually {
        val span = reporter.nextSpan().value
        span.operationName shouldBe StatementTypes.Query
        span.tags("component") shouldBe TagValue.String("jdbc")
        span.tags("db.statement").toString should include("SELECT * FROM Address where Nr = 3")
        reporter.nextSpan() shouldBe None
      }
    }

    "generate Spans on calls to .executeQuery(sql) in statements" in {
      val select = s"SELECT * FROM Address where Nr = 4"
      val resultSet = connection.createStatement().executeQuery(select)
      validateNextRow(resultSet, valueNr = 4, valueName = "faa")

      eventually {
        val span = reporter.nextSpan().value
        span.operationName shouldBe StatementTypes.Query
        span.tags("component") shouldBe TagValue.String("jdbc")
        span.tags("db.statement").toString should include("SELECT * FROM Address where Nr = 4")
        reporter.nextSpan() shouldBe None
      }
    }

    "generate Spans on calls to .executeUpdate() in prepared statements" in {
      val insert = s"INSERT INTO Address (Nr, Name) VALUES(1, 'foo')"
      val affectedRows = connection.prepareStatement(insert).executeUpdate()
      affectedRows shouldBe 1

      eventually {
        val span = reporter.nextSpan().value
        span.operationName shouldBe StatementTypes.Update
        span.tags("component") shouldBe TagValue.String("jdbc")
        span.tags("db.statement").toString should include("INSERT INTO Address (Nr, Name) VALUES(1, 'foo')")
        reporter.nextSpan() shouldBe None
      }
    }

    "generate Spans on calls to .executeUpdate(sql) in statements" in {
      val insert = s"INSERT INTO Address (Nr, Name) VALUES(2, 'foo')"
      val affectedRows = connection.createStatement().executeUpdate(insert)
      affectedRows shouldBe 1

      eventually {
        val span = reporter.nextSpan().value
        span.operationName shouldBe StatementTypes.Update
        span.tags("component") shouldBe TagValue.String("jdbc")
        span.tags("db.statement").toString should include("INSERT INTO Address (Nr, Name) VALUES(2, 'foo')")
        reporter.nextSpan() shouldBe None
      }
    }
  }

  private def validateNextRow(resultSet: ResultSet, valueNr: Int, valueName: String, shouldBeMore: Boolean = false): Unit = {
    resultSet.next() shouldBe true
    resultSet.getInt("Nr") shouldBe valueNr
    resultSet.getString("Name") shouldBe valueName
    resultSet.next() shouldBe shouldBeMore
  }

  var registration: Registration = _
  var connection:Connection = _
  val db = DB.newEmbeddedDB(3306)
  val reporter = new TestSpanReporter()


  override protected def beforeAll(): Unit = {
    db.start()
    connection = DriverManager.getConnection("jdbc:mysql://localhost/test", "root", "")
    connection
      .prepareStatement("CREATE TABLE Address (Nr INTEGER, Name VARCHAR(128));")
      .executeUpdate()

    connection.createStatement().executeUpdate(s"INSERT INTO Address (Nr, Name) VALUES(3, 'foo')")
    connection.createStatement().executeUpdate(s"INSERT INTO Address (Nr, Name) VALUES(4, 'faa')")

    Metrics.Statements.InFlight.refine().distribution()

    enableFastSpanFlushing()
    sampleAlways()
    registration = Kamon.addReporter(reporter)
  }

  override protected def afterAll(): Unit = {
    db.stop()
    registration.cancel()
  }
}