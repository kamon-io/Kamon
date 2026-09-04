package kamon.instrumentation.instrumentation

import java.net.InetAddress
import java.util.concurrent.TimeUnit

import com.datastax.driver.core.{
  Cluster,
  HostDistance,
  PerHostPercentileTracker,
  PlainTextAuthProvider,
  PoolingOptions,
  SocketOptions
}
import com.datastax.driver.core.policies.{
  DCAwareRoundRobinPolicy,
  DefaultRetryPolicy,
  LatencyAwarePolicy,
  LoggingRetryPolicy,
  TokenAwarePolicy
}
import com.datastax.driver.core.querybuilder.QueryBuilder
import javax.management.Query
import kamon.Kamon

import scala.util.Random

object TestApp extends App {

  Kamon.init()

  val poolingOptions = new PoolingOptions()
    .setConnectionsPerHost(HostDistance.REMOTE, 50, 256)
    .setConnectionsPerHost(HostDistance.LOCAL, 50, 256)
    .setMaxRequestsPerConnection(HostDistance.LOCAL, 32768)
    .setMaxRequestsPerConnection(HostDistance.REMOTE, 2000)

  val contactPoints = "127.0.0.1"

  val hostTracker = PerHostPercentileTracker.builder(1000).build()

  val dcRoundRobinPolicy = DCAwareRoundRobinPolicy
    .builder()
    .withLocalDc("datacenter1")
    .build()

  val latencyAwarePolicy = LatencyAwarePolicy
    .builder(dcRoundRobinPolicy)
    .withExclusionThreshold(1.5)
    .withMininumMeasurements(50) //default
    .withRetryPeriod(1, TimeUnit.MINUTES) //default 10ms
    .withScale(100, TimeUnit.MILLISECONDS) //default
    .withUpdateRate(100, TimeUnit.MILLISECONDS)
    .build()

  //Node level timeout, 1 sec, allow for one retry by default policy
  val socketOptions = new SocketOptions()
    .setReadTimeoutMillis(2200)

  val cluster = Cluster
    .builder()
    .addContactPoint(contactPoints)
    .withSocketOptions(socketOptions)
    .withRetryPolicy(new LoggingRetryPolicy(DefaultRetryPolicy.INSTANCE))
    .withLoadBalancingPolicy(new TokenAwarePolicy(latencyAwarePolicy))
    .withPoolingOptions(poolingOptions)
    .build()

  val session = cluster.connect("test")
  /*CREATE TABLE test.t1 (
    txt text PRIMARY KEY,
    num int
) */

  session.execute("TRUNCATE test.t1")

  for (i <- 1 to 20) {
    Kamon.runWithSpan(Kamon.spanBuilder("inserting-batch").start()) {
      Kamon.currentSpan().tag("span.kind", "server")
      session.execute(
        s"INSERT INTO test.t1 (txt,num) values ('${i}', ${Random.nextInt(100)})"
      )
    }
  }

  val q = QueryBuilder.select("txt", "num").from("t1").setFetchSize(4)
  import scala.collection.JavaConverters._
  val c = Kamon.runWithSpan(Kamon.spanBuilder("query").start()) {
    Kamon.currentSpan().tag("span.kind", "server")
    session.execute(q).iterator().asScala.foreach { r =>
      Thread.sleep(10)
    }
  }

  Thread.sleep(10000)
  System.exit(-1)

}
