/*
 * =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.datadog

import akka.testkit.{ TestKitBase, TestProbe }
import akka.actor.{ ActorRef, Props, ActorSystem }
import org.scalatest.{ Matchers, WordSpecLike }
import kamon.metrics._
import akka.io.Udp
import org.HdrHistogram.HdrRecorder
import kamon.metrics.Subscriptions.TickMetricSnapshot
import java.lang.management.ManagementFactory
import java.net.InetSocketAddress
import com.typesafe.config.ConfigFactory

class DatadogMetricSenderSpec extends TestKitBase with WordSpecLike with Matchers {
}
