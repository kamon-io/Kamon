/*
 * =========================================================================================
 * Copyright © 2013-2016 the kamon project <http://kamon.io/>
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

package kamon.akka.http

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes.{ BadRequest, OK }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import kamon.Kamon
import kamon.testkit.BaseKamonSpec
import org.scalatest.Matchers

class AkkaHttpServerMetricsSpec extends BaseKamonSpec with ScalatestRouteTest with Matchers {

  override protected def beforeAll(): Unit = {
    Kamon.start()

  }

  //  override def system: ActorSystem = {
  //    Kamon.start()
  //    ActorSystem("")
  //  }

  val routes =
    get {
      path("record-trace-metrics-ok") {
        complete(OK)
      } ~
        path("record-trace-metrics-bad-request") {
          complete(BadRequest)
        } ~
        path("record-http-metrics-ok") {
          complete(OK)
        } ~
        path("record-http-metrics-bad-request") {
          complete(BadRequest)
        }
    }

  "the Akka Http Server metrics instrumentation" should {
    "record trace metrics for processed requests" in {
      for (repetition ← 1 to 10) {
        Get("/record-trace-metrics-ok") ~> routes ~> check {
          status shouldBe OK
        }
      }

      for (repetition ← 1 to 5) {
        Get("/record-trace-metrics-bad-request") ~> routes ~> check {
          status shouldEqual BadRequest
        }
      }

      val snapshot = takeSnapshotOf("UnnamedTrace", "trace")
      snapshot.histogram("elapsed-time").get.numberOfMeasurements should be(15)
    }

    "record http server metrics for all the requests" in {
      // Erase metrics recorder from previous tests.
      takeSnapshotOf("akka-http-server", "http-server")

      for (repetition ← 1 to 10) {
        Get("/record-http-metrics-ok") ~> routes ~> check {
          status shouldEqual OK
        }
      }

      for (repetition ← 1 to 5) {
        Get("/record-http-metrics-bad-request") ~> routes ~> check {
          status shouldEqual BadRequest
        }
      }

      val snapshot = takeSnapshotOf("akka-http-server", "http-server")
      snapshot.counter("UnnamedTrace_200").get.count should be(10)
      snapshot.counter("UnnamedTrace_400").get.count should be(5)
      snapshot.counter("200").get.count should be(10)
      snapshot.counter("400").get.count should be(5)
    }
  }
}