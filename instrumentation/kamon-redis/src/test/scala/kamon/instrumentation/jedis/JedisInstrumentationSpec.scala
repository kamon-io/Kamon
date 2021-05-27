package kamon.instrumentation.jedis

import kamon.testkit.{MetricInspection, TestSpanReporter}
import kamon.trace.Span.Kind
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Matchers, OptionValues, WordSpec}
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import redis.clients.jedis.Jedis

import scala.concurrent.duration.DurationInt
import scala.util.control.NonFatal


class JedisInstrumentationSpec extends WordSpec
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

  "the Jedis instrumentation" should {
    "generate a client span for get and set commands" in {
      val jedis = new Jedis(container.getHost, container.getFirstMappedPort)
      jedis.set("foo", "bar")

      eventually(timeout(10.seconds)) {
        val span = testSpanReporter().nextSpan().get
        span.operationName shouldBe "redis.command.SET"
        span.kind shouldBe Kind.Client
      }

      testSpanReporter().clear()

      jedis.get("foo")
      eventually(timeout(10.seconds)) {
        val span = testSpanReporter().nextSpan().get
        span.operationName shouldBe "redis.command.GET"
        span.kind shouldBe Kind.Client
      }
    }
  }
}
