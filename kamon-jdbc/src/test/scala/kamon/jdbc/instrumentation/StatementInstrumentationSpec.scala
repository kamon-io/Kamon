/* =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

import java.sql.{ DriverManager, SQLException }

import com.typesafe.config.ConfigFactory
import kamon.jdbc.{ Jdbc, JdbcNameGenerator, SqlErrorProcessor, SlowQueryProcessor }
import kamon.metric.TraceMetricsSpec
import kamon.testkit.BaseKamonSpec
import kamon.trace.{ Tracer, SegmentCategory }

class StatementInstrumentationSpec extends BaseKamonSpec("jdbc-spec") {
  override lazy val config =
    ConfigFactory.parseString(
      """
        |kamon {
        |   jdbc {
        |     slow-query-threshold = 100 milliseconds
        |
        |     # Fully qualified name of the implementation of kamon.jdbc.SlowQueryProcessor.
        |     slow-query-processor = kamon.jdbc.instrumentation.NoOpSlowQueryProcessor
        |
        |     # Fully qualified name of the implementation of kamon.jdbc.SqlErrorProcessor.
        |     sql-error-processor = kamon.jdbc.instrumentation.NoOpSqlErrorProcessor
        |
        |     # Fully qualified name of the implementation of kamon.jdbc.JdbcNameGenerator
        |     name-generator = kamon.jdbc.instrumentation.NoOpJdbcNameGenerator
        |   }
        |}
      """.stripMargin)

  val connection = DriverManager.getConnection("jdbc:h2:mem:jdbc-spec", "SA", "")

  override protected def beforeAll(): Unit = {
    connection should not be null

    val create = "CREATE TABLE Address (Nr INTEGER, Name VARCHAR(128));"
    val createStatement = connection.createStatement()
    createStatement.executeUpdate(create)

    val sleep = "CREATE ALIAS SLEEP FOR \"java.lang.Thread.sleep(long)\""
    val sleepStatement = connection.createStatement()
    sleepStatement.executeUpdate(sleep)
  }

  "the StatementInstrumentation" should {
    "record the execution time of INSERT operation" in {
      Tracer.withContext(newContext("jdbc-trace-insert")) {
        for (id ← 1 to 100) {
          val insert = s"INSERT INTO Address (Nr, Name) VALUES($id, 'foo')"
          val insertStatement = connection.prepareStatement(insert)
          insertStatement.execute()
        }

        Tracer.currentContext.finish()
      }

      val jdbcSnapshot = takeSnapshotOf("jdbc-statements", "jdbc-statements")
      jdbcSnapshot.histogram("writes").get.numberOfMeasurements should be(100)

      val traceSnapshot = takeSnapshotOf("jdbc-trace-insert", "trace")
      traceSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(1)

      val segmentSnapshot = takeSnapshotOf("Jdbc[Insert]", "trace-segment",
        tags = Map(
          "trace" -> "jdbc-trace-insert",
          "category" -> SegmentCategory.Database,
          "library" -> Jdbc.SegmentLibraryName))

      segmentSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(100)
    }

    "record the execution time of SELECT operation" in {
      Tracer.withContext(newContext("jdbc-trace-select")) {
        for (id ← 1 to 100) {
          val select = s"SELECT * FROM Address where Nr = $id"
          val selectStatement = connection.createStatement()
          selectStatement.execute(select)
        }

        Tracer.currentContext.finish()
      }

      val jdbcSnapshot = takeSnapshotOf("jdbc-statements", "jdbc-statements")
      jdbcSnapshot.histogram("reads").get.numberOfMeasurements should be(100)

      val traceSnapshot = takeSnapshotOf("jdbc-trace-select", "trace")
      traceSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(1)

      val segmentSnapshot = takeSnapshotOf("Jdbc[Select]", "trace-segment",
        tags = Map(
          "trace" -> "jdbc-trace-select",
          "category" -> SegmentCategory.Database,
          "library" -> Jdbc.SegmentLibraryName))

      segmentSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(100)
    }

    "record the execution time of UPDATE operation" in {
      Tracer.withContext(newContext("jdbc-trace-update")) {
        for (id ← 1 to 100) {
          val update = s"UPDATE Address SET Name = 'bar$id' where Nr = $id"
          val updateStatement = connection.prepareStatement(update)
          updateStatement.execute()
        }

        Tracer.currentContext.finish()
      }

      val jdbcSnapshot = takeSnapshotOf("jdbc-statements", "jdbc-statements")
      jdbcSnapshot.histogram("writes").get.numberOfMeasurements should be(100)

      val traceSnapshot = takeSnapshotOf("jdbc-trace-update", "trace")
      traceSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(1)

      val segmentSnapshot = takeSnapshotOf("Jdbc[Update]", "trace-segment",
        tags = Map(
          "trace" -> "jdbc-trace-update",
          "category" -> SegmentCategory.Database,
          "library" -> Jdbc.SegmentLibraryName))

      segmentSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(100)
    }

    "record the execution time of DELETE operation" in {
      Tracer.withContext(newContext("jdbc-trace-delete")) {
        for (id ← 1 to 100) {
          val delete = s"DELETE FROM Address where Nr = $id"
          val deleteStatement = connection.createStatement()
          deleteStatement.execute(delete)
        }

        Tracer.currentContext.finish()
      }

      val jdbcSnapshot = takeSnapshotOf("jdbc-statements", "jdbc-statements")
      jdbcSnapshot.histogram("writes").get.numberOfMeasurements should be(100)

      val traceSnapshot = takeSnapshotOf("jdbc-trace-delete", "trace")
      traceSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(1)

      val segmentSnapshot = takeSnapshotOf("Jdbc[Delete]", "trace-segment",
        tags = Map(
          "trace" -> "jdbc-trace-delete",
          "category" -> SegmentCategory.Database,
          "library" -> Jdbc.SegmentLibraryName))

      segmentSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(100)

    }

    "record the execution time of SLOW QUERIES based on the kamon.jdbc.slow-query-threshold" in {
      Tracer.withContext(newContext("jdbc-trace-slow")) {
        for (id ← 1 to 2) {
          val select = s"SELECT * FROM Address; CALL SLEEP(100)"
          val selectStatement = connection.createStatement()
          selectStatement.execute(select)
        }

        Tracer.currentContext.finish()
      }

      val jdbcSnapshot = takeSnapshotOf("jdbc-statements", "jdbc-statements")
      jdbcSnapshot.counter("slows").get.count should be(2)

    }

    "count all SQL ERRORS" in {
      Tracer.withContext(newContext("jdbc-trace-errors")) {
        for (_ ← 1 to 10) {
          intercept[SQLException] {
            val error = "SELECT * FROM NO_EXISTENT_TABLE"
            val errorStatement = connection.createStatement()
            errorStatement.execute(error)
          }
        }

        Tracer.currentContext.finish()
      }

      val jdbcSnapshot = takeSnapshotOf("jdbc-statements", "jdbc-statements")
      jdbcSnapshot.counter("errors").get.count should be(10)
    }
  }
}

class NoOpSlowQueryProcessor extends SlowQueryProcessor {
  override def process(sql: String, executionTimeInMillis: Long, queryThresholdInMillis: Long): Unit = { /*do nothing!!!*/ }
}

class NoOpSqlErrorProcessor extends SqlErrorProcessor {
  override def process(sql: String, ex: Throwable): Unit = { /*do nothing!!!*/ }
}

class NoOpJdbcNameGenerator extends JdbcNameGenerator {
  override def generateJdbcSegmentName(statement: String): String = s"Jdbc[$statement]"
}