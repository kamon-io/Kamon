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

import java.sql.DriverManager
import java.util.concurrent.Executors

import kamon.Kamon
import kamon.jdbc.{JdbcExtension, SlowQueryProcessor, SqlErrorProcessor}
import kamon.metric.EntitySnapshot
import kamon.trace.{SegmentCategory, Tracer}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

class StatementInstrumentationSpec extends WordSpec with Matchers with Eventually with SpanSugar with BeforeAndAfterAll {
  val connection = DriverManager.getConnection("jdbc:h2:mem:jdbc-spec;MULTI_THREADED=1", "SA", "")
  implicit val parallelQueriesExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10))

  override protected def beforeAll(): Unit = {
    connection
      .prepareStatement("CREATE TABLE Address (Nr INTEGER, Name VARCHAR(128));")
      .executeUpdate()

    connection
      .prepareStatement("CREATE ALIAS SLEEP FOR \"java.lang.Thread.sleep(long)\"")
      .executeUpdate()

    takeSnapshotOf("non-pooled", "jdbc-statement")
  }

  "the StatementInstrumentation" should {
    "track in-flight operations" in {
      val operations = for (id ← 1 to 10) yield {
        Future {
          DriverManager
            .getConnection("jdbc:h2:mem:jdbc-spec", "SA", "")
            .prepareStatement(s"SELECT * FROM Address where Nr = $id; CALL SLEEP(500)")
            .execute()
        }
      }

      Await.result(Future.sequence(operations), 2 seconds)
      takeSnapshotOf("non-pooled", "jdbc-statement").minMaxCounter("in-flight").get.max should be(10)
    }

    "track calls to .execute(..) in statements" in {
      for (id ← 1 to 100) {
        val select = s"SELECT * FROM Address where Nr = $id"
        connection.prepareStatement(select).execute()
        connection.createStatement().execute(select)
      }

      val jdbcSnapshot = takeSnapshotOf("non-pooled", "jdbc-statement")
      jdbcSnapshot.histogram("generic-execute").get.numberOfMeasurements should be(200)
      jdbcSnapshot.histogram("queries").get.numberOfMeasurements should be(0)
      jdbcSnapshot.histogram("updates").get.numberOfMeasurements should be(0)
      jdbcSnapshot.histogram("batches").get.numberOfMeasurements should be(0)
    }

    "track calls to .executeQuery(..) in statements" in {
      for (id ← 1 to 100) {
        val select = s"SELECT * FROM Address where Nr = $id"
        connection.prepareStatement(select).executeQuery()
        connection.createStatement().executeQuery(select)
      }

      val jdbcSnapshot = takeSnapshotOf("non-pooled", "jdbc-statement")
      jdbcSnapshot.histogram("queries").get.numberOfMeasurements should be(200)
      jdbcSnapshot.histogram("updates").get.numberOfMeasurements should be(0)
      jdbcSnapshot.histogram("batches").get.numberOfMeasurements should be(0)
      jdbcSnapshot.histogram("generic-execute").get.numberOfMeasurements should be(0)
    }

    "track calls to .executeUpdate(..) in statements" in {
      for (id ← 1 to 100) {
        val insert = s"INSERT INTO Address (Nr, Name) VALUES($id, 'foo')"
        connection.prepareStatement(insert).executeUpdate()
        connection.createStatement().executeUpdate(insert)
      }

      val jdbcSnapshot = takeSnapshotOf("non-pooled", "jdbc-statement")
      jdbcSnapshot.histogram("updates").get.numberOfMeasurements should be(200)
      jdbcSnapshot.histogram("queries").get.numberOfMeasurements should be(0)
      jdbcSnapshot.histogram("batches").get.numberOfMeasurements should be(0)
      jdbcSnapshot.histogram("generic-execute").get.numberOfMeasurements should be(0)
    }

    "track calls to .executeBatch() in statements" in {
      for (id ← 1 to 100) {
        val statement = connection.prepareStatement("INSERT INTO Address (Nr, Name) VALUES(?, 'foo')")
        statement.setInt(1, id)
        statement.addBatch()

        statement.setInt(1, id)
        statement.addBatch()
        statement.executeBatch()
      }

      val jdbcSnapshot = takeSnapshotOf("non-pooled", "jdbc-statement")
      jdbcSnapshot.histogram("batches").get.numberOfMeasurements should be(100)
      jdbcSnapshot.histogram("updates").get.numberOfMeasurements should be(0)
      jdbcSnapshot.histogram("queries").get.numberOfMeasurements should be(0)
      jdbcSnapshot.histogram("generic-execute").get.numberOfMeasurements should be(0)
    }

    "track errors when executing statements" in {
      for (id ← 1 to 100) {
        val insert = s"INSERT INTO NotATable (Nr, Name) VALUES($id, 'foo')"
        val select = s"SELECT * FROM NotATable where Nr = $id"

        Try(connection.createStatement().execute(select))
        Try(connection.createStatement().executeQuery(select))
        Try(connection.createStatement().executeUpdate(insert))
      }

      val jdbcSnapshot = takeSnapshotOf("non-pooled", "jdbc-statement")
      jdbcSnapshot.counter("errors").get.count should be(300)
    }

    "record the execution time of SLOW QUERIES based on the kamon.jdbc.slow-query-threshold setting" in {
      takeSnapshotOf("non-pooled", "jdbc-statement")

      for (id ← 1 to 2) {
        val select = s"SELECT * FROM Address; CALL SLEEP(500)"
        connection.createStatement().execute(select)
      }

      val jdbcSnapshot = takeSnapshotOf("non-pooled", "jdbc-statement")
      jdbcSnapshot.counter("slows-statements").get.count should be(2)
    }

    "generate segments when DB access is made within a Trace" in {
      Tracer.withNewContext("jdbc-trace-with-segments") {
        for (id ← 1 to 100) {
          val insert = s"INSERT INTO Address (Nr, Name) VALUES($id, 'foo')"
          val select = s"SELECT * FROM Address where Nr = $id"

          connection.createStatement().execute(select)
          connection.createStatement().executeQuery(select)
          connection.createStatement().executeUpdate(insert)
          connection.prepareStatement(insert).executeBatch()
        }

        Tracer.currentContext.finish()
      }

      Seq("query", "update", "batch", "generic-execute").foreach { segmentType ⇒
        val segmentSnapshot = takeSnapshotOf("jdbc-" + segmentType, "trace-segment",
          tags = Map(
            "trace" -> "jdbc-trace-with-segments",
            "category" -> SegmentCategory.Database,
            "library" -> JdbcExtension.SegmentLibraryName))

        segmentSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(100)
      }
    }
  }

  def takeSnapshotOf(name: String, category: String, tags: Map[String, String] = Map.empty): EntitySnapshot = {
    val recorder = Kamon.metrics.find(name, category, tags).get
    recorder.collect(Kamon.metrics.buildDefaultCollectionContext)
  }
}

class NoOpSlowQueryProcessor extends SlowQueryProcessor {
  override def process(sql: String, executionTimeInMillis: Long, queryThresholdInMillis: Long): Unit = { /*do nothing!!!*/ }
}

class NoOpSqlErrorProcessor extends SqlErrorProcessor {
  override def process(sql: String, ex: Throwable): Unit = { /*do nothing!!!*/ }
}