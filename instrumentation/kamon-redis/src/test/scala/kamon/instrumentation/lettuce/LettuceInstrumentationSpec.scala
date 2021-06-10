package kamon.instrumentation.lettuce

import io.lettuce.core.RedisClient
import kamon.testkit.{MetricInspection, TestSpanReporter}
import kamon.trace.Span.Kind
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, Matchers, OptionValues, WordSpec}
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

import java.time.Duration
import scala.concurrent.duration.DurationInt
import scala.util.control.NonFatal

class LettuceInstrumentationSpec extends WordSpec
  with Matchers
  with ScalaFutures
  with Eventually
  with BeforeAndAfterAll
  with MetricInspection.Syntax
  with OptionValues
  with TestSpanReporter {

  var container: GenericContainer[Nothing] = _
  override def beforeAll: Unit = {
    val REDIS_IMAGE = DockerImageName.parse("redis")
    container = new GenericContainer(REDIS_IMAGE).withExposedPorts(6379)
    container.start()
  }

  override def afterAll: Unit = {
    container.stop()
  }

  "the Lettuce instrumentation" should {
    "generate a client span for async commands" in {
      val client = RedisClient.create(s"redis://${container.getHost}:${container.getFirstMappedPort}")
      val connection = client.connect
      val asyncCommands = connection.async()
      asyncCommands.set("key", "Hello, Redis!")

      eventually(timeout(2.seconds)) {
        val span = testSpanReporter().nextSpan().get
        span.operationName shouldBe "redis.command.SET"
        span.kind shouldBe Kind.Client
        testSpanReporter().spans() shouldBe empty
      }
      connection.close()
    }

    "generate a client span for sync commands" in {
      val client = RedisClient.create(s"redis://${container.getHost}:${container.getFirstMappedPort}")
      val connection = client.connect
      val commands = connection.sync()

      commands.get("key")

      eventually(timeout(2.seconds)) {
        val span = testSpanReporter().nextSpan().get
        span.operationName shouldBe "redis.command.GET"
        span.kind shouldBe Kind.Client
        testSpanReporter().spans() shouldBe empty
      }
      client.shutdown()
    }

    "fail a span that times out" in {
      val client = RedisClient.create(s"redis://${container.getHost}:${container.getFirstMappedPort}")
      client.setDefaultTimeout(Duration.ofNanos(1))
      val connection = client.connect
      val commands = connection.sync()

      try {
        commands.get("key")
      } catch {
        case NonFatal(x) => println(s"Task failed successfully")
      }

      eventually(timeout(2.seconds)) {
        val span = testSpanReporter().nextSpan().get
        span.operationName shouldBe "redis.command.GET"
        span.kind shouldBe Kind.Client
        span.hasError shouldBe true
        testSpanReporter().spans() shouldBe empty
      }
      client.shutdown()
    }
  }
}
