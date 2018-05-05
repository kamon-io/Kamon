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

import java.sql.{Connection, DriverManager}

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