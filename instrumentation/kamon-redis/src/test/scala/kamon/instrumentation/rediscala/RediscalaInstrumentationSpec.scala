package kamon.instrumentation.rediscala

import kamon.testkit.{MetricInspection, TestSpanReporter}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import org.scalatest.{BeforeAndAfterAll, Matchers, OptionValues, WordSpec}
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import redis.{RedisBlockingClient, RedisClient}

class RediscalaInstrumentationSpec extends WordSpec
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
    container = new GenericContainer(REDIS_IMAGE)
      .withExposedPorts(6379)

    container.start()
  }

  override def afterAll: Unit = {
    container.stop()
  }

  "the Rediscala instrumentation" should {
    implicit val akkaSystem = akka.actor.ActorSystem()
    "generate only one client span for commands" in {
      val client = RedisClient(host = container.getHost, port = container.getFirstMappedPort)
      client.set("a", "a")

      eventually(timeout(2.seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "redis.command.Set"
        span.hasError shouldBe false
      }
    }

    "generate only one client span when using the blocking client" in {
      val blockingClient = RedisBlockingClient(host = container.getHost, port = container.getFirstMappedPort)
      blockingClient.blpop(Seq("a", "b", "c"))

      eventually(timeout(5.seconds)) {
        val span = testSpanReporter().nextSpan().value
        println(span)
        span.operationName shouldBe "redis.command.Blpop"
        span.hasError shouldBe true
      }
    }
  }
}
