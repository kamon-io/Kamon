package kamon.instrumentation

import com.dimafeng.testcontainers.{ElasticsearchContainer, ForAllTestContainer}
import kamon.tag.Lookups.plain
import kamon.testkit.{Reconfigure, TestSpanReporter}
import org.apache.http.HttpHost
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.impl.client.{BasicCredentialsProvider, HttpClientBuilder}
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.admin.cluster.node.tasks.list.{ListTasksRequest, ListTasksResponse}
import org.elasticsearch.client._
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar
import org.scalatest.{BeforeAndAfterAll, Matchers, OptionValues, WordSpec}


class ElasticSearchInstrumentationTest
  extends WordSpec
    with Matchers
    with Eventually
    with SpanSugar
    with Reconfigure
    with OptionValues
    with TestSpanReporter
    with BeforeAndAfterAll
    with ForAllTestContainer {

  val endpointTag = "elasticsearch.http.endpoint"
  val methodTag = "elasticsearch.http.method"

  "The elasticsearch client" should {
    "records a span for a basic sync request" in {
      client.performRequest(new Request("GET", "/_cluster/health"))

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName should be("elasticsearch/SyncRequest")
        span.tags.get(plain(endpointTag)) shouldBe "/_cluster/health"
        span.tags.get(plain(methodTag)) shouldBe "GET"
      }
    }

    "records a span for a basic async request" in {
      client.performRequestAsync(new Request("GET", "/_cluster/health"),
        new ResponseListener() {
          override def onSuccess(response: Response): Unit = ()
          override def onFailure(exception: Exception): Unit = ()
        })

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName should be("elasticsearch/AsyncRequest")
        span.tags.get(plain(endpointTag)) shouldBe "/_cluster/health"
        span.tags.get(plain(methodTag)) shouldBe "GET"
      }
    }

    "records a span for a high level sync request" in {
      highLevelClient.tasks().list(new ListTasksRequest(), RequestOptions.DEFAULT)

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName should be("elasticsearch/ListTasksRequest")
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
        span.operationName should be("elasticsearch/ListTasksRequest")
        span.tags.get(plain(endpointTag)) shouldBe "/_tasks"
        span.tags.get(plain(methodTag)) shouldBe "GET"
      }
    }
  }


  override val container = ElasticsearchContainer()
  var client: RestClient = _
  var highLevelClient: RestHighLevelClient = _

  override protected def beforeAll(): Unit = {
    container.start()

    client = RestClient
      .builder(HttpHost.create(container.httpHostAddress))
      .build

    highLevelClient = new RestHighLevelClient(
      RestClient.builder(HttpHost.create(container.httpHostAddress)))
  }

  override protected def afterAll(): Unit = {
    container.stop()
  }
}
