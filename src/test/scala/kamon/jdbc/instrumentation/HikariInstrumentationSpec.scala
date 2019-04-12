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

package kamon.jdbc.instrumentation

import java.sql.SQLException
import java.util.concurrent.Executors

import com.typesafe.config.ConfigFactory
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import kamon.Kamon
import kamon.jdbc.Metrics
import kamon.tag.TagSet
import kamon.testkit.{InstrumentInspection, MetricInspection}
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Milliseconds, Seconds, Span, SpanSugar}
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.ExecutionContext

class HikariInstrumentationSpec extends WordSpec with Matchers with Eventually with SpanSugar with MetricInspection.Syntax with InstrumentInspection.Syntax {
  implicit val parallelQueriesContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(16))


  "the Hikari instrumentation" should {

    Kamon.reconfigure(
      ConfigFactory.parseString("kamon.metric.tick-interval=1s")
        .withFallback(Kamon.config())
    )


    "create a entity tracking each hikari pool using the pool name as entity name and cleanup after closing the pool" in {
      val pool1 = createPool("example-1")
      val pool2 = createPool("example-2")

      Metrics.openConnections.tagValues("poolName") should contain allOf(
        "example-1",
        "example-2"
      )

      pool1.close()
      pool2.close()

      Thread.sleep(2000)
      Metrics.openConnections.tagValues("poolName") shouldBe empty
    }

    "track the number of open connections to the database" in {
      val pool = createPool("track-open-connections", 16)

      val connections = (1 to 10) map { _ ⇒
        pool.getConnection()
      }

      val tags = TagSet.from(
        Map(
          "poolVendor" -> "hikari",
          "poolName" -> "track-open-connections"
        )
      )

      eventually(timeout(15 seconds), interval(200 millis)) {
        Metrics.openConnections.withTags(tags).distribution(false).max shouldBe (10)
      }

      connections.foreach(_.close())

      eventually(timeout(15 seconds),  interval(1 second)) {
        Metrics.openConnections.withTags(tags).distribution(false).max shouldBe (0)
      }

      pool.close()
    }

    "track the number of borrowed connections" in {
      val pool = createPool("track-borrowed-connections", 16)
      val connections = (1 to 10) map { _ ⇒
        pool.getConnection()
      }


      val tags = TagSet.from(
        Map(
          "poolVendor" -> "hikari",
          "poolName" -> "track-borrowed-connections"
        )
      )

      eventually(timeout(30 seconds),  interval(1 second)) {
        Metrics.borrowedConnections.withTags(tags).distribution().max shouldBe (10)
      }

      connections.drop(5).foreach(_.close())

      eventually(timeout(30 seconds),  interval(1 second)) {
        Metrics.borrowedConnections.withTags(tags).distribution().max shouldBe (5)
      }

      pool.close()
    }


    "track the time it takes to borrow a connection" in {
      val pool = createPool("track-borrow-time", 5)
      for (_ ← 1 to 5) {
        pool.getConnection()
      }

      val tags = TagSet.from(
        Map(
          "poolVendor" -> "hikari",
          "poolName" -> "track-borrow-time"
        )
      )


      eventually {
        Metrics.borrowTime.withTags(tags).distribution(resetState = false).count shouldBe (6) // 5 + 1 during setup
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
          case ex =>
            println(ex.getMessage)
            throw ex
        }
      }

      val tags = TagSet.from(
        Map(
          "poolVendor" -> "hikari",
          "poolName" -> "track-borrow-timeouts"
        )
      )

      eventually(timeout(30 seconds),  interval(1 second)) {
        Metrics.borrowTime.withTags(tags).distribution(resetState = false).max shouldBe ((1 seconds).toNanos +- (100 milliseconds).toNanos)
      }

      Metrics.borrowTimeouts.withTags(tags).value() shouldBe 1
    }

  }

  def createPool(name: String, size: Int = 1): HikariDataSource = {
    System.setProperty("com.zaxxer.hikari.housekeeping.periodMs", "200")

    val config = new HikariConfig()
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

