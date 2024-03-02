package kamon.instrumentation.combined

import io.lettuce.core.{RedisClient => LettuceClient}
import kamon.tag.Lookups
import kamon.tag.Lookups._
import kamon.testkit.{InitAndStopKamonAfterAll, MetricInspection, TestSpanReporter}
import kamon.trace.Span.Kind
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.OptionValues
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import redis.clients.jedis.Jedis
import redis.{RedisBlockingClient, RedisClient}

import java.time.Duration
import scala.concurrent.duration.DurationInt
import scala.util.control.NonFatal

class RedisInstrumentationsSpec extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with Eventually
    with InitAndStopKamonAfterAll
    with MetricInspection.Syntax
    with OptionValues
    with TestSpanReporter {

  private val logger = LoggerFactory.getLogger(classOf[RedisInstrumentationsSpec])
  var container: GenericContainer[Nothing] = _

  override def beforeAll: Unit = {
    super.beforeAll()
    val REDIS_IMAGE = DockerImageName.parse("redis")
    container = new GenericContainer(REDIS_IMAGE).withExposedPorts(6379)

    container.start()
  }

  override def afterAll: Unit = {
    container.stop()
    super.afterAll()
  }

  "the Jedis instrumentation" should {
    "generate a client span for get and set commands" in {
      val jedis = new Jedis(container.getHost, container.getFirstMappedPort)
      jedis.set("foo", "bar")

      eventually(timeout(2.seconds)) {
        val span = testSpanReporter().nextSpan().get
        span.kind shouldBe Kind.Client
        span.operationName shouldBe "SET"
        span.metricTags.get(plain("db.system")) shouldBe "redis"
        span.tags.get(plain("db.operation")) shouldBe "SET"
        testSpanReporter().spans() shouldBe empty
      }

      testSpanReporter().clear()

      jedis.get("foo")
      eventually(timeout(2.seconds)) {
        val span = testSpanReporter().nextSpan().get
        span.kind shouldBe Kind.Client
        span.operationName shouldBe "GET"
        span.metricTags.get(plain("db.system")) shouldBe "redis"
        span.tags.get(plain("db.operation")) shouldBe "GET"
        testSpanReporter().spans() shouldBe empty
      }
    }
  }

  "the Lettuce instrumentation" should {
    "generate a client span for async commands" in {
      val client = LettuceClient.create(s"redis://${container.getHost}:${container.getFirstMappedPort}")
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
      val client = LettuceClient.create(s"redis://${container.getHost}:${container.getFirstMappedPort}")
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
      val client = LettuceClient.create(s"redis://${container.getHost}:${container.getFirstMappedPort}")
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

  "the Rediscala instrumentation" should {
    implicit val akkaSystem = akka.actor.ActorSystem()
    "generate only one client span for commands" in {
      val client = RedisClient(host = container.getHost, port = container.getFirstMappedPort)
      client.set("a", "a")

      eventually(timeout(30.seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "redis.command.Set"
        span.hasError shouldBe false
      }
      client.shutdown()
    }

    "generate only one client span when using the blocking client" in {
      val blockingClient = RedisBlockingClient(host = container.getHost, port = container.getFirstMappedPort)
      blockingClient.blpop(Seq("a", "b", "c"))

      eventually(timeout(30.seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "redis.command.Blpop"
        span.hasError shouldBe true
      }
      blockingClient.stop()
    }

  }
}
