/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
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

import java.sql.SQLException
import java.util.concurrent.Executors

import com.typesafe.config.ConfigFactory
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import kamon.Kamon
import kamon.instrumentation.jdbc.StatementMonitor.StatementTypes
import kamon.tag.Lookups.plain
import kamon.tag.TagSet
import kamon.testkit.{InstrumentInspection, MetricInspection, TestSpanReporter}
import org.scalactic.TimesOnInt.convertIntToRepeater
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Milliseconds, Seconds, Span, SpanSugar}
import org.scalatest.{Matchers, OptionValues, WordSpec}

import scala.concurrent.ExecutionContext

class HikariInstrumentationSpec extends WordSpec with Matchers with Eventually with SpanSugar with MetricInspection.Syntax
    with InstrumentInspection.Syntax with TestSpanReporter with OptionValues {

  import HikariInstrumentationSpec.createPool
  implicit val parallelQueriesContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(16))

  Kamon.reconfigure(
    ConfigFactory
      .parseString("kamon.metric.tick-interval=1s")
      .withFallback(Kamon.config())
  )

  "the Hikari instrumentation" should {
    "track each hikari pool using the pool name as tag and cleanup after closing the pool" in {
      val pool1 = createPool("example-1")
      val pool2 = createPool("example-2")

      JdbcMetrics.OpenConnections.tagValues("jdbc.pool.name") should contain allOf(
        "example-1",
        "example-2"
      )

      pool1.close()
      pool2.close()

      eventually {
        JdbcMetrics.OpenConnections.tagValues("jdbc.pool.name") shouldBe empty
      }
    }

    "track the number of open connections to the database" in {
      val pool = createPool("track-open-connections", 16)
      val connections = (1 to 10) map { _ ⇒ pool.getConnection() }

      val tags = TagSet.builder()
        .add("jdbc.pool.vendor", "hikari")
        .add("jdbc.pool.name", "track-open-connections")
        .add("db.vendor", "h2")
        .build()

      eventually(timeout(15 seconds), interval(200 millis)) {
        JdbcMetrics.OpenConnections.withTags(tags).distribution(false).max shouldBe (10)
      }

      connections.foreach(_.close())

      eventually(timeout(15 seconds),  interval(1 second)) {
        JdbcMetrics.OpenConnections.withTags(tags).distribution(true).max shouldBe (0)
      }

      pool.close()
    }

    "track the number of borrowed connections" in {
      val pool = createPool("track-borrowed-connections", 16)
      val connections = (1 to 10) map { _ ⇒
        pool.getConnection()
      }

      val tags = TagSet.builder()
        .add("jdbc.pool.vendor", "hikari")
        .add("jdbc.pool.name", "track-borrowed-connections")
        .add("db.vendor", "h2")
        .build()

      eventually(timeout(30 seconds),  interval(1 second)) {
        JdbcMetrics.BorrowedConnections.withTags(tags).distribution().max shouldBe (10)
      }

      connections.drop(5).foreach(_.close())

      eventually(timeout(30 seconds),  interval(1 second)) {
        JdbcMetrics.BorrowedConnections.withTags(tags).distribution().max shouldBe (5)
      }

      pool.close()
    }


    "track the time it takes to borrow a connection" in {
      val pool = createPool("track-borrow-time", 5)
      for (_ ← 1 to 5) {
        pool.getConnection()
      }

      val tags = TagSet.builder()
        .add("jdbc.pool.vendor", "hikari")
        .add("jdbc.pool.name", "track-borrow-time")
        .add("db.vendor", "h2")
        .build()

      eventually {
        JdbcMetrics.BorrowTime.withTags(tags).distribution(resetState = false).count shouldBe (6) // 5 + 1 during setup
      }
    }

    "track timeout errors when borrowing a connection" in {
      val pool = createPool("track-borrow-timeouts", 5)
      for (id ← 1 to 5) {
        pool.getConnection()
      }

      intercept[SQLException] {
        try {
          pool.getConnection()
        } catch {
          case ex: Throwable =>
            println(ex.getMessage)
            throw ex
        }
      }

      val tags = TagSet.builder()
        .add("jdbc.pool.vendor", "hikari")
        .add("jdbc.pool.name", "track-borrow-timeouts")
        .add("db.vendor", "h2")
        .build()

      eventually(timeout(30 seconds),  interval(1 second)) {
        JdbcMetrics.BorrowTime.withTags(tags).distribution(resetState = false).max shouldBe ((1 seconds).toNanos +- (100 milliseconds).toNanos)
      }

      JdbcMetrics.BorrowTimeouts.withTags(tags).value() shouldBe 1
    }

    "add the pool information to the execution of the connection init SQL" in {
      val pool = createPool("connection-init", 5)
      5 times { pool.getConnection() }

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "connection-init"
        span.metricTags.get(plain("component")) shouldBe "jdbc"
        span.metricTags.get(plain("db.vendor")) shouldBe "h2"
        span.metricTags.get(plain("jdbc.pool.vendor")) shouldBe "hikari"
        span.metricTags.get(plain("jdbc.pool.name")) shouldBe "connection-init"
        span.tags.get(plain("db.statement")) should include("SELECT 1;")
      }
    }
  }
}

object HikariInstrumentationSpec {

  def createPool(name: String, size: Int = 1): HikariDataSource = {
    System.setProperty("com.zaxxer.hikari.housekeeping.periodMs", "200")

    val config = new HikariConfig()
    config.setConnectionInitSql("SELECT 1;")
    config.setPoolName(name)
    config.setJdbcUrl(s"jdbc:h2:mem:$name;MULTI_THREADED=1")
    config.setUsername("SA")
    config.setPassword("")
    config.setMinimumIdle(1)
    config.setMaximumPoolSize(size)
    config.setConnectionTimeout(1000)
    config.setIdleTimeout(10000) // If this setting is lower than 10 seconds it will be overridden by Hikari.

    val hikariPool = new HikariDataSource(config)
    val setupConnection = hikariPool.getConnection()
    setupConnection
      .prepareStatement(
        """|CREATE TABLE Address (Nr INTEGER, Name VARCHAR(128));
           |CREATE ALIAS SLEEP FOR "java.lang.Thread.sleep(long)";
        """.stripMargin)
      .executeUpdate()
    setupConnection.close()

    hikariPool
  }
}

