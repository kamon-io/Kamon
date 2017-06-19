package kamon.datadog

/*

import org.asynchttpclient.DefaultAsyncHttpClient
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.asynchttpclient.Response
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit

import com.typesafe.config.Config
import kamon.MetricReporter
import kamon.metric.TickSnapshot

/**
 * Sends metrics to Datadog through its native HTTPS API.
 */
class DatadogAPIMetricsSender extends MetricReporter {

  override def start(): Unit = ???

  override def stop(): Unit = ???

  override def reconfigure(config: Config): Unit = ???

  override def reportTickSnapshot(snapshot: TickSnapshot): Unit = ???

  val config = context.system.settings.config.getConfig("kamon.datadog")
  val appName = config.getString("application-name")
  val client = new DefaultAsyncHttpClient(new DefaultAsyncHttpClientConfig.Builder()
    .setConnectTimeout(config.getDuration("http.connect-timeout", TimeUnit.MILLISECONDS).toInt)
    .setReadTimeout(config.getDuration("http.read-timeout", TimeUnit.MILLISECONDS).toInt)
    .setRequestTimeout(config.getDuration("http.request-timeout", TimeUnit.MILLISECONDS).toInt)
    .build)


  val url = "https://app.datadoghq.com/api/v1/series?api_key=" + config.getString("http.api-key") // FIXME url encode
  val host = config.getString("http.host-override") match {
    case "none" => systemHostName
    case s => s
  }

  val ready: Receive = {
    case tick: TickMetricSnapshot =>
      send(tick)
      context become sending
  }

  val sending: Receive = {
    case tick: TickMetricSnapshot =>
      log.warning("Dropping some metrics since previous datadog HTTP request hasn't responded yet.")

    case Status.Failure(x) =>
      log.error(x, "Datadog request failed, some metrics may have been dropped")
      context become ready

    case resp:Response =>
      if (resp.getStatusCode < 200 || resp.getStatusCode > 299) {
        log.error("Datadog request failed, some metrics may have been dropped: {}", resp)
      }
      context become ready
  }

  override def receive = ready

  def send(tick: TickMetricSnapshot): Unit = {
    val time:Long = tick.from.millis

    val series:String = (for {
      (groupIdentity, groupSnapshot) ← tick.metrics
      (metricIdentity, metricSnapshot) ← groupSnapshot.metrics
    } yield {
      val key = buildMetricName(groupIdentity, metricIdentity)
      val tags = groupIdentity.tags.map { case (k,v) => "\"" + k + ":" + v + "\"" }.mkString(",")
      def emit(keyPostfix: String, metricType: String, value: Double): String =
        s"""["metric":"${key}${keyPostfix}","points":[[${time},${value}]],"type":"${metricType}","host":"${host}","tags":[${tags}]]"""

      metricSnapshot match {
        case hs: Histogram.Snapshot ⇒
          Seq(
            emit(".min", "gauge", hs.min),
            emit(".max", "gauge", hs.max),
            emit(".cnt", "counter", hs.numberOfMeasurements),
            emit(".sum", "gauge", hs.sum),
            emit(".p95", "gauge", hs.percentile(0.95))
          )
        case cs: Counter.Snapshot ⇒
          if (cs.count > 0) Seq(emit("", "counter", cs.count)) else Seq()
      }
    }).flatten.mkString(",")
    val body = series

    client.preparePost(url).setBody(body).setHeader("Content-Type", "application/json").execute().toCompletableFuture.toScala pipeTo self
  }


  def systemHostName: String = ManagementFactory.getRuntimeMXBean.getName.split('@')(1)
}
*/

