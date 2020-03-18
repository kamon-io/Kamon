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

package kamon.instrumentation.jdbc

import java.sql.{Connection, DriverManager, ResultSet}
import java.time.Duration
import java.util.concurrent.Executors

import ch.vorburger.mariadb4j.DB
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.instrumentation.jdbc.StatementMonitor.StatementTypes
import kamon.testkit.{InstrumentInspection, MetricInspection, Reconfigure, TestSpanReporter}
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar
import org.scalatest.{BeforeAndAfterAll, Matchers, OptionValues, WordSpec}
import kamon.tag.Lookups._
import kamon.tag.TagSet

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class StatementInstrumentationSpec
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

  implicit val parallelQueriesExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10))

  Kamon.reconfigure(
    ConfigFactory
      .parseString("""kamon.trace.hooks.pre-start = [ "kamon.trace.Hooks$PreStart$FromContext" ]""")
      .withFallback(Kamon.config())
  )

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(2 seconds))

  val drivers = Seq(
    DriverSuite.H2,
    DriverSuite.SQLite,
    DriverSuite.MySQL,
    DriverSuite.HikariH2
  ).filter(canRunInCurrentEnvironment)

  "the StatementInstrumentation" when {
    drivers.foreach { driver =>
      driver.init()
      val connection = driver.connect()

      s"instrumenting the ${driver.name} driver" should {
        "track in-flight operations" in {
          if (driver.supportSleeping) {
            val vendorTags = TagSet.of("db.vendor", driver.vendor)

            for (_ ← 1 to 10) yield Future {
              val connection = driver.connect()
              driver.sleep(connection, Duration.ofMillis(500))
            }

            eventually(timeout(2 seconds)) {
              JdbcMetrics.InFlightStatements.withTags(vendorTags).distribution().max shouldBe 10
            }

            eventually(timeout(2 seconds)) {
              JdbcMetrics.InFlightStatements.withTags(vendorTags).distribution().max shouldBe 0
            }

            testSpanReporter().clear()
          }
        }

        "generate Spans on calls to .execute() in prepared statements" in {
          val select = s"SELECT * FROM Address where Nr = ?"
          val statement = connection.prepareStatement(select)
          statement.setLong(1, 1)
          statement.execute()
          validateNextRow(statement.getResultSet, valueNr = 1, valueName = "foo")

          eventually(timeout(scaled(5 seconds))) {
            val span = testSpanReporter().nextSpan().value
            span.operationName shouldBe StatementTypes.GenericExecute
            span.metricTags.get(plain("component")) shouldBe "jdbc"
            span.metricTags.get(plain("db.vendor")) shouldBe driver.vendor
            span.tags.get(plain("db.url")) shouldBe driver.url
            span.tags.get(plain("db.statement")) should include("SELECT * FROM Address where Nr = ?")
            testSpanReporter().nextSpan() shouldBe None
          }
        }

        "generate Spans on calls to .execute(sql) in statements" in {
          val select = s"SELECT * FROM Address where Nr = 2"
          val statement = connection.createStatement()
          statement.execute(select)
          validateNextRow(statement.getResultSet, valueNr = 2, valueName = "foo")

          eventually {
            val span = testSpanReporter().nextSpan().value
            span.operationName shouldBe StatementTypes.GenericExecute
            span.metricTags.get(plain("component")) shouldBe "jdbc"
            span.metricTags.get(plain("db.vendor")) shouldBe driver.vendor
            span.tags.get(plain("db.url")) shouldBe driver.url
            span.tags.get(plain("db.statement")) should include("SELECT * FROM Address where Nr = 2")
            testSpanReporter().nextSpan() shouldBe None
          }
        }

        "generate Spans on calls to .executeQuery() in prepared statements" in {
          val select = s"SELECT * FROM Address where Nr = 3"
          val statement = connection.prepareStatement(select)
          val rs = statement.executeQuery()
          validateNextRow(rs, valueNr = 3, valueName = "foo")

          eventually {
            val span = testSpanReporter().nextSpan().value
            span.operationName shouldBe StatementTypes.Query
            span.metricTags.get(plain("component")) shouldBe "jdbc"
            span.metricTags.get(plain("db.vendor")) shouldBe driver.vendor
            span.tags.get(plain("db.url")) shouldBe driver.url
            span.tags.get(plain("db.statement")) should include("SELECT * FROM Address where Nr = 3")
            testSpanReporter().nextSpan() shouldBe None
          }
        }

        "generate Spans on calls to .executeQuery(sql) in statements" in {
          val select = s"SELECT * FROM Address where Nr = 4"
          val statement = connection.createStatement()
          val rs = statement.executeQuery(select)
          validateNextRow(rs, valueNr = 4, valueName = "foo")

          eventually {
            val span = testSpanReporter().nextSpan().value
            span.operationName shouldBe StatementTypes.Query
            span.metricTags.get(plain("component")) shouldBe "jdbc"
            span.metricTags.get(plain("db.vendor")) shouldBe driver.vendor
            span.tags.get(plain("db.url")) shouldBe driver.url
            span.tags.get(plain("db.statement")) should include("SELECT * FROM Address where Nr = 4")
            testSpanReporter().nextSpan() shouldBe None
          }
        }

        "generate Spans on calls to .executeUpdate() in prepared statements" in {
          val insert = s"INSERT INTO Address (Nr, Name) VALUES(5, 'foo')"
          val affectedRows = connection.prepareStatement(insert).executeUpdate()
          affectedRows shouldBe 1

          eventually {
            val span = testSpanReporter().nextSpan().value
            span.operationName shouldBe StatementTypes.Update
            span.metricTags.get(plain("component")) shouldBe "jdbc"
            span.metricTags.get(plain("db.vendor")) shouldBe driver.vendor
            span.tags.get(plain("db.url")) shouldBe driver.url
            span.tags.get(plain("db.statement")) should include("INSERT INTO Address (Nr, Name) VALUES(5, 'foo')")
            testSpanReporter().nextSpan() shouldBe None
          }
        }

        "generate Spans on calls to .executeUpdate(sql) in statements" in {
          val insert = s"INSERT INTO Address (Nr, Name) VALUES(6, 'foo')"
          val affectedRows = connection.createStatement().executeUpdate(insert)
          affectedRows shouldBe 1

          eventually {
            val span = testSpanReporter().nextSpan().value
            span.operationName shouldBe StatementTypes.Update
            span.metricTags.get(plain("component")) shouldBe "jdbc"
            span.metricTags.get(plain("db.vendor")) shouldBe driver.vendor
            span.tags.get(plain("db.url")) shouldBe driver.url
            span.tags.get(plain("db.statement")) should include("INSERT INTO Address (Nr, Name) VALUES(6, 'foo')")
            testSpanReporter().nextSpan() shouldBe None
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
            val span = testSpanReporter().nextSpan().value
            span.operationName shouldBe StatementTypes.Batch
            span.metricTags.get(plain("component")) shouldBe "jdbc"
            span.metricTags.get(plain("db.vendor")) shouldBe driver.vendor
            span.tags.get(plain("db.url")) shouldBe driver.url
            span.tags.get(plain("db.statement")).toString should include("INSERT INTO Address (Nr, Name) VALUES(?, 'foo')")
            testSpanReporter().nextSpan() shouldBe None
          }
        }

        "add errors to Spans when errors happen" in {
          val insert = s"INSERT INTO NotATable (Nr, Name) VALUES(1, 'foo')"
          val select = s"SELECT * FROM NotATable where Nr = 1"

          Try(connection.createStatement().execute(select))

          eventually {
            val span = testSpanReporter().nextSpan().value
            span.operationName shouldBe StatementTypes.GenericExecute
            span.metricTags.get(plain("component")) shouldBe "jdbc"
            span.metricTags.get(plain("db.vendor")) shouldBe driver.vendor
            span.tags.get(plain("db.url")) shouldBe driver.url
            span.metricTags.get(plainBoolean("error")) shouldBe true
            span.tags.get(option("error.stacktrace")) should be('defined)
            testSpanReporter().nextSpan() shouldBe None
          }

          Try(connection.createStatement().executeQuery(select))

          eventually {
            val span = testSpanReporter().nextSpan().value
            span.operationName shouldBe StatementTypes.Query
            span.metricTags.get(plain("component")) shouldBe "jdbc"
            span.metricTags.get(plain("db.vendor")) shouldBe driver.vendor
            span.tags.get(plain("db.url")) shouldBe driver.url
            span.metricTags.get(plainBoolean("error")) shouldBe true
            span.tags.get(option("error.stacktrace")) should be('defined)
            testSpanReporter().nextSpan() shouldBe None
          }

          Try(connection.createStatement().executeUpdate(insert))

          eventually {
            val span = testSpanReporter().nextSpan().value
            span.operationName shouldBe StatementTypes.Update
            span.metricTags.get(plain("component")) shouldBe "jdbc"
            span.metricTags.get(plainBoolean("error")) shouldBe true
            span.metricTags.get(plain("db.vendor")) shouldBe driver.vendor
            span.tags.get(plain("db.url")) shouldBe driver.url
          }
        }

        "rethrow the exception when error happen" in {
          val select = s"SELECT * FROM NotATable where Nr = 1"

          Try(connection.createStatement().execute(select)).failed.get.getMessage
            .toLowerCase() should include("notatable")
        }
      }
    }
  }

  private def validateNextRow(resultSet: ResultSet, valueNr: Int, valueName: String, shouldBeMore: Boolean = false): Unit = {
    resultSet.next() shouldBe true
    resultSet.getInt("Nr") shouldBe valueNr
    resultSet.getString("Name") shouldBe valueName
    resultSet.next() shouldBe shouldBeMore
  }

  trait DriverSuite {

    /**
      * Name of the driver being tested.
      */
    def name: String

    /**
      * Vendor string to be used in tags
      */
    def vendor: String

    /**
      * Database URL to be used in span tags
      */
    def url: String

    /**
      * Defines whether it is possible to sleep a connection from this driver
      */
    def supportSleeping: Boolean

    /**
      * Opens a new connection to a predefined test database.
      */
    def connect(): Connection

    /**
      * Use the provided connection to setup the "Address" table.
      */
    def init(): Unit

    /**
      * Issues a execute (or similar) call on the connection that should last for approximately the provided duration.
      */
    def sleep(connection: Connection, duration: Duration): Unit

  }

  object DriverSuite {

    object H2 extends DriverSuite with AddressTableSetup {
      val name = "H2"
      val vendor = "h2"
      val url = "jdbc:h2:mem:jdbc-spec;MULTI_THREADED=1"
      val supportSleeping = true

      override def init(): Unit = {
        val connection = connect()
        initializeAddressTable(connection)
        connection.prepareStatement("CREATE ALIAS SLEEP FOR \"java.lang.Thread.sleep(long)\"").executeUpdate()
      }

      override def connect(): Connection =
        DriverManager.getConnection(url, "SA", "")

      override def sleep(connection: Connection, duration: Duration): Unit =
        connection.prepareStatement(s"SELECT 1; CALL SLEEP(${duration.toMillis})").execute()
    }

    object HikariH2 extends DriverSuite with AddressTableSetup {
      val name = "H2 behind Hikari"
      val vendor = "h2"
      val url = "jdbc:h2:mem:hikari-tracing-spec;MULTI_THREADED=1"
      val supportSleeping = false
      val pool = HikariInstrumentationSpec.createH2Pool("hikari-tracing-spec", 20)

      override def init(): Unit = {
        val connection = connect()
        initializeAddressTable(connection)
      }

      override def connect(): Connection =
        pool.getConnection

      override def sleep(connection: Connection, duration: Duration): Unit =
        connection.prepareStatement(s"SELECT 1; CALL SLEEP(${duration.toMillis})").execute()
    }

    object SQLite extends DriverSuite with AddressTableSetup {
      val name = "SQLite"
      val vendor = "sqlite"
      val url = "jdbc:sqlite::memory:"
      val supportSleeping = false
      lazy val connection = DriverManager.getConnection(url)

      override def init(): Unit =
        initializeAddressTable(connection)

      override def connect(): Connection =
        connection

      override def sleep(connection: Connection, duration: Duration): Unit = {}

    }

    object MySQL extends DriverSuite with AddressTableSetup {
      val name = "MySQL"
      val vendor = "mysql"
      val url = "jdbc:mysql://localhost/test"
      val supportSleeping = false

      override def init(): Unit = {
        DB.newEmbeddedDB(3306).start()
        initializeAddressTable(connect())
      }

      override def connect(): Connection =
        DriverManager.getConnection(url)

      override def sleep(connection: Connection, duration: Duration): Unit = {}

    }

    trait AddressTableSetup {
      def initializeAddressTable(connection: Connection): Unit = {
        connection.createStatement().executeUpdate("DROP TABLE IF EXISTS Address;")
        connection.createStatement().executeUpdate("CREATE TABLE Address (Nr INTEGER, Name VARCHAR(128));")
        connection.createStatement().executeUpdate(s"INSERT INTO Address (Nr, Name) VALUES(1, 'foo')")
        connection.createStatement().executeUpdate(s"INSERT INTO Address (Nr, Name) VALUES(2, 'foo')")
        connection.createStatement().executeUpdate(s"INSERT INTO Address (Nr, Name) VALUES(3, 'foo')")
        connection.createStatement().executeUpdate(s"INSERT INTO Address (Nr, Name) VALUES(4, 'foo')")
      }
    }
  }

  def canRunInCurrentEnvironment(driverSuite: DriverSuite): Boolean =
    !(System.getenv("TRAVIS") != null && driverSuite.vendor == "mysql")
}
