/*
 * =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.netty

import kamon.netty.Clients.withNioClient
import kamon.netty.Metrics.{registeredChannelsMetric, taskProcessingTimeMetric, taskQueueSizeMetric, taskWaitingTimeMetric}
import kamon.netty.Servers.{withEpollServer, withNioServer}
import kamon.testkit.{InstrumentInspection, MetricInspection}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class NettyMetricsSpec extends AnyWordSpec with Matchers with MetricInspection.Syntax with InstrumentInspection.Syntax  {

  "The NettyMetrics" should {

    "track the NioEventLoop in boss-group and worker-group" in {
      withNioServer() { port =>
        withNioClient(port) { httpClient =>
          val httpGet = httpClient.get(s"http://localhost:$port/route?param=123")
          httpClient.execute(httpGet)

          registeredChannelsMetric.tagValues("name") should contain atLeastOneOf("boss-group-nio-event-loop", "worker-group-nio-event-loop")
          taskProcessingTimeMetric.tagValues("name") should contain atLeastOneOf("boss-group-nio-event-loop", "worker-group-nio-event-loop")
          taskQueueSizeMetric.tagValues("name") should contain atLeastOneOf("boss-group-nio-event-loop", "worker-group-nio-event-loop")
          taskWaitingTimeMetric.tagValues("name") should contain atLeastOneOf("boss-group-nio-event-loop", "worker-group-nio-event-loop")
        }
      }
    }

    "track the EpollEventLoop in boss-group and worker-group" in {
      withEpollServer() { port =>
        withNioClient(port) { httpClient =>
          val httpGet = httpClient.get(s"http://localhost:$port/route?param=123")
          httpClient.execute(httpGet)

          registeredChannelsMetric.tagValues("name") should contain atLeastOneOf("boss-group-epoll-event-loop", "worker-group-epoll-event-loop")
          taskProcessingTimeMetric.tagValues("name") should contain atLeastOneOf("boss-group-epoll-event-loop", "worker-group-epoll-event-loop")
          taskQueueSizeMetric.tagValues("name") should contain atLeastOneOf("boss-group-epoll-event-loop", "worker-group-epoll-event-loop")
          taskWaitingTimeMetric.tagValues("name") should contain atLeastOneOf("boss-group-epoll-event-loop", "worker-group-epoll-event-loop")
        }
      }
    }

    "track the registered channels, task processing time and task queue size for NioEventLoop" in {
      withNioServer() { port =>
        withNioClient(port) { httpClient =>
          val httpGet = httpClient.get(s"http://localhost:$port/route?param=123")
          val response = httpClient.execute(httpGet)
          response.status.code() should be(200)

          registeredChannelsMetric.tagValues("name") should contain("boss-group-nio-event-loop")

          val metrics = Metrics.forEventLoop("boss-group-nio-event-loop")

          metrics.registeredChannels.distribution().max should be > 0L
          metrics.taskProcessingTime.distribution().max should be > 0L
          metrics.taskQueueSize.distribution().max should be > 0L
          metrics.taskWaitingTime.distribution().max should be > 0L
        }
      }
    }

    "track the registered channels, task processing time and task queue size for EpollEventLoop" in {
      withEpollServer() { port =>
        withNioClient(port) { httpClient =>
          val httpGet = httpClient.get(s"http://localhost:$port/route?param=123")
          val response = httpClient.execute(httpGet)
          response.status.code() should be(200)

          registeredChannelsMetric.tagValues("name") should contain("boss-group-epoll-event-loop")

          val metrics = Metrics.forEventLoop("boss-group-epoll-event-loop")

          metrics.registeredChannels.distribution().max should be >= 0L
          metrics.taskProcessingTime.distribution().max should be > 0L
          metrics.taskQueueSize.distribution().max should be >= 0L
          metrics.taskWaitingTime.distribution().max should be > 0L
        }
      }
    }
  }
}


