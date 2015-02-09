/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

package kamon.annotation

import akka.actor.ActorSystem
import kamon.Kamon
import kamon.annotation.instrumentation.AnnotationBla
import kamon.metric.{CounterKey, EntitySnapshot, Metrics, TraceMetrics}
import kamon.testkit.BaseKamonSpec
import org.scalatest.{Matchers, WordSpecLike}

class AnnotationSpec extends BaseKamonSpec("trace-metrics-spec") with WordSpecLike with Matchers {

//  implicit lazy val system: ActorSystem = AnnotationBla.system

  "The AnnotationSpec" should {
    "blablabla trace" in {
      val a = new Annotated
      for(_ <- 1 to 100) {
        a.greeting()
      }
      val snapshot = takeSnapshotOf("greeting","trace")
//      snapshot.elapsedTime.numberOfMeasurements should be(100)
//      snapshot.segments shouldBe empty
    }

    "blablabla counter" in {
      val a = new Annotated
      for(_ <- 1 to 100) {
        a.count()
      }
      val metricsExtension = Kamon(Metrics)
//      metricsExtension.find(Counter("my-counter"))
    }

    "blablabla gauge" in {
      val a = new Annotated
      for(_ <- 1 to 100) {
        a.getSomeValue()
        a.time()
      }
      val metricsExtension = Kamon(Metrics)
//      metricsExtension.storage.keys should contain(UserGauge("gauge"))
    }
  }
}

class Annotated(val a:Int = 1) {

  @Trace("greeting")
  def greeting():Unit ={}

  @Counted(name = "#{this.a}", metadata = """#{'a':'b'}""")
  def count():Unit = {}

  @Counted(name = "my-counter")
  def count2():Unit = {}

  @Timed(name = "my-time")
  def time():Unit = {}

  @Gauge(name = "gauge")
  def getSomeValue():Int = 15
}