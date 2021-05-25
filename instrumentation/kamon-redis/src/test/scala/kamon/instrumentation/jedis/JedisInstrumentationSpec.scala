package kamon.instrumentation.jedis

import kamon.metric.MeasurementUnit.time.seconds
import kamon.testkit.{MetricInspection, TestSpanReporter}
import kamon.trace.Span.Kind
import org.scalatest.{BeforeAndAfterAll, Matchers, OptionValues, WordSpec}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import redis.clients.jedis.Jedis

import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
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
  override def beforeAll = {
    val REDIS_IMAGE = DockerImageName.parse("redis")
    container = new GenericContainer(REDIS_IMAGE).withExposedPorts(6379)

    container.start()
  }

  override def afterAll= {
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

      jedis.get("foo")
      eventually(timeout(10.seconds)) {
        val spans = testSpanReporter().spans()
        val span = spans.head
        span.operationName shouldBe "redis.command.GET"
        span.kind shouldBe Kind.Client
        spans.size shouldBe 1
      }
    }

    "fail the span when encountering an error" in {
      val jedis = new Jedis(container.getHost, container.getFirstMappedPort)
      try {
        jedis.zscan("fake", "fake")
      } catch {
        case NonFatal(e) => println("ZScan failed successfully")
      }

      eventually(timeout(10.seconds)) {
        val span = testSpanReporter().nextSpan().get
        span.operationName shouldBe "redis.command.ZSCAN"
        span.kind shouldBe Kind.Client
        span.hasError shouldBe true
      }
    }

  }
}
