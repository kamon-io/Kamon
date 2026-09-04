package kamon.instrumentation

import kamon.tag.Lookups.plain
import kamon.testkit.{InitAndStopKamonAfterAll, Reconfigure, TestSpanReporter}
import org.apache.http.HttpHost
import org.opensearch.action.ActionListener
import org.opensearch.action.admin.cluster.node.tasks.list.{ListTasksRequest, ListTasksResponse}
import org.opensearch.client._
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, OptionValues}
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait

class OpenSearchInstrumentationTest
    extends AnyWordSpec
    with Matchers
    with Eventually
    with SpanSugar
    with Reconfigure
    with OptionValues
    with TestSpanReporter
    with InitAndStopKamonAfterAll
    with BeforeAndAfterAll {

  val endpointTag = "opensearch.http.endpoint"
  val methodTag = "opensearch.http.method"

  "The opensearch client" should {
    "records a span for a basic sync request" in {
      client.performRequest(new Request("GET", "/_cluster/health"))

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName should be("opensearch/SyncRequest")
        span.tags.get(plain(endpointTag)) shouldBe "/_cluster/health"
        span.tags.get(plain(methodTag)) shouldBe "GET"
      }
    }

    "records a span for a basic async request" in {
      client.performRequestAsync(
        new Request("GET", "/_cluster/health"),
        new ResponseListener() {
          override def onSuccess(response: Response): Unit = ()
          override def onFailure(exception: Exception): Unit = ()
        }
      )

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName should be("opensearch/AsyncRequest")
        span.tags.get(plain(endpointTag)) shouldBe "/_cluster/health"
        span.tags.get(plain(methodTag)) shouldBe "GET"
      }
    }

    "records a span for a high level sync request" in {
      highLevelClient.tasks().list(new ListTasksRequest(), RequestOptions.DEFAULT)

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName should be("opensearch/ListTasksRequest")
        span.tags.get(plain(endpointTag)) shouldBe "/_tasks"
        span.tags.get(plain(methodTag)) shouldBe "GET"
      }
    }

    "records a span for a high level async request" in {
      val request = new ListTasksRequest()
      val listener = new ActionListener[ListTasksResponse] {
        override def onResponse(response: ListTasksResponse): Unit = ()
        override def onFailure(e: Exception): Unit = ()
      }

      highLevelClient.tasks().listAsync(request, RequestOptions.DEFAULT, listener)

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName should be("opensearch/ListTasksRequest")
        span.tags.get(plain(endpointTag)) shouldBe "/_tasks"
        span.tags.get(plain(methodTag)) shouldBe "GET"
      }
    }
  }

  val container = new GenericContainer("opensearchproject/opensearch:1.3.14")
  var client: RestClient = _
  var highLevelClient: RestHighLevelClient = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    
    container.addExposedPort(9200)
    container.addEnv("discovery.type", "single-node")
    container.addEnv("plugins.security.disabled", "true")
    container.setWaitStrategy(Wait.forHttp("/_cluster/health"))
    container.start()

    val hostAddress = s"${container.getHost}:${container.getMappedPort(9200)}"
    
    client = RestClient
      .builder(HttpHost.create(hostAddress))
      .build()

    highLevelClient = new RestHighLevelClient(
      RestClient.builder(HttpHost.create(hostAddress))
    )
  }

  override protected def afterAll(): Unit = {
    container.stop()
    super.afterAll()
  }
}
