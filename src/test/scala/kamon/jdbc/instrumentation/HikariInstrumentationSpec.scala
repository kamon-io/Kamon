package kamon.jdbc.instrumentation

import java.util.concurrent.Executors

import com.zaxxer.hikari.{ HikariConfig, HikariDataSource }
import kamon.Kamon
import kamon.metric.EntitySnapshot
import org.scalatest.{ Matchers, WordSpec }
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.util.Try

class HikariInstrumentationSpec extends WordSpec with Matchers with Eventually with SpanSugar {
  implicit val parallelQueriesContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(16))

  "the Hikari instrumentation" should {
    "create a entity tracking each hikari pool using the pool name as entity name and cleanup after closing the pool" in {
      val pool1 = createPool("example-1")
      val pool2 = createPool("example-2")
      Kamon.metrics.find("example-1", "hikari-pool") shouldBe defined
      Kamon.metrics.find("example-2", "hikari-pool") shouldBe defined

      pool1.close()
      pool2.close()
      Kamon.metrics.find("example-1", "hikari-pool") shouldBe empty
      Kamon.metrics.find("example-2", "hikari-pool") shouldBe empty

    }

    "track the number of open connections to the database" in {
      val pool = createPool("track-open-connections", 16)
      val connections = (1 to 10) map { _ ⇒
        pool.getConnection()
      }

      val openConnections = takeSnapshotOf("track-open-connections", "hikari-pool").minMaxCounter("open-connections").get
      openConnections.min shouldBe (0)
      openConnections.max shouldBe (10)

      connections.foreach(_.close())

      eventually(timeout(15 seconds), interval(1 second)) {
        takeSnapshotOf("track-open-connections", "hikari-pool")
          .minMaxCounter("open-connections")
          .get
          .max shouldBe (1)
      }
    }

    "track the number of borrowed connections" in {
      val pool = createPool("track-borrowed-connections", 16)
      val connections = (1 to 7) map { _ ⇒
        pool.getConnection()
      }

      val openConnections = takeSnapshotOf("track-borrowed-connections", "hikari-pool").minMaxCounter("borrowed-connections").get
      openConnections.min shouldBe (0)
      openConnections.max shouldBe (7)

      connections.drop(3).foreach(_.close())

      eventually {
        takeSnapshotOf("track-borrowed-connections", "hikari-pool")
          .minMaxCounter("borrowed-connections")
          .get
          .max shouldBe (3)
      }

      pool.close()
    }

    "track the number of in-flight operations in connections from the pool" in {
      val pool = createPool("track-in-flight", 16)
      for (id ← 1 to 10) {
        Future {
          pool.getConnection()
            .prepareStatement(s"SELECT * from Address; CALL SLEEP(1000);")
            .execute()
        }
      }

      eventually(timeout(2 seconds), interval(100 millis)) {
        takeSnapshotOf("track-in-flight", "hikari-pool").minMaxCounter("in-flight").get.max should be(10)
      }
    }

    "track standard metrics for statements executed with connection from the pool" in {
      val pool = createPool("track-metrics", 10)
      takeSnapshotOf("track-metrics", "hikari-pool")

      val operations = for (id ← 1 to 10) yield {
        Future {
          val connection = pool.getConnection()
          val insert = s"INSERT INTO Address (Nr, Name) VALUES($id, 'foo')"
          val select = s"SELECT * FROM Address where Nr = $id"
          val badQuery = "SELECT * FROM NotATable"

          connection.createStatement().execute(select)
          connection.createStatement().executeQuery(select)
          connection.createStatement().executeUpdate(insert)
          connection.prepareStatement(insert).executeBatch()
          Try(connection.createStatement().execute(badQuery))
        }
      }

      Await.result(Future.sequence(operations), 10 seconds)

      val poolSnapshot = takeSnapshotOf("track-metrics", "hikari-pool")
      poolSnapshot.histogram("generic-execute").get.numberOfMeasurements should be(20)
      poolSnapshot.histogram("queries").get.numberOfMeasurements should be(10)
      poolSnapshot.histogram("updates").get.numberOfMeasurements should be(10)
      poolSnapshot.histogram("batches").get.numberOfMeasurements should be(10)
      poolSnapshot.counter("errors").get.count should be(10)
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

  def takeSnapshotOf(name: String, category: String): EntitySnapshot = {
    val recorder = Kamon.metrics.find(name, category).get
    recorder.collect(Kamon.metrics.buildDefaultCollectionContext)
  }
}
