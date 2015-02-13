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
import com.typesafe.config.{ConfigFactory, Config}
import kamon.Kamon
import kamon.annotation.Segment
import kamon.annotation.instrumentation.AnnotationBla
import kamon.metric.{TraceMetricsSpec, EntitySnapshot, Metrics}
import kamon.testkit.BaseKamonSpec
import kamon.trace.TraceContext
import org.scalatest.{Matchers, WordSpecLike}

import scala.annotation.tailrec

class AnnotationSpec extends WordSpecLike with Matchers {
  import TraceMetricsSpec.SegmentSyntax

  lazy val collectionContext = AnnotationBla.kamon.metrics.buildDefaultCollectionContext
  implicit lazy val system: ActorSystem = AnnotationBla.kamon.actorSystem

  def config: Config =
    ConfigFactory.load()

  def newContext(name: String): TraceContext =
    AnnotationBla.kamon.tracer.newContext(name)

  def newContext(name: String, token: String): TraceContext =
    AnnotationBla.kamon.tracer.newContext(name, token)

  def takeSnapshotOf(name: String, category: String): EntitySnapshot = {
    val recorder = AnnotationBla.kamon.metrics.find(name, category).get
    recorder.collect(collectionContext)
  }

  "The AnnotationSpec" should {
    "blablabla trace" in {
      val a = new Annotated
      for(_ <- 1 to 100) {
        a.greeting()
      }

      val snapshot = takeSnapshotOf("greeting", "trace")
      snapshot.histogram("elapsed-time").get.numberOfMeasurements should be(100)
      snapshot.segments.size should be(0)
    }

    "blablabla segment" in {
      val a = new Annotated
      for(_ <- 1 to 100) {
        a.greeting2()
      }

      val snapshot = takeSnapshotOf("greeting2", "trace")
      snapshot.histogram("elapsed-time").get.numberOfMeasurements should be(100)
      snapshot.segments.size should be(1)
    }

    "blablabla counter" in {
      val a = new Annotated()

      for(_ <- 1 to 100) {
        a.count()
      }

      val snapshot = takeSnapshotOf("user-metric", "user-metric")
      snapshot.counter("bla:1").get.count should be (100)
    }

    "blablabla histogram" in {
      val a = new Annotated
      for(_ <- 1 to 100) {
        a.getSomeValue()
      }
      val snapshot = takeSnapshotOf("user-metric", "user-metric")
      snapshot.histogram("histogram").get.numberOfMeasurements should be (100)
      snapshot.histogram("histogram").get.max should be (15)
    }

    "blablabla minmax" in {
      val a = new Annotated
      for(_ <- 1 to 100) {
        a.count3()
      }
      val snapshot = takeSnapshotOf("user-metric", "user-metric")
      snapshot.minMaxCounter("min-max-counter").get.sum should be (1)
    }

    "blablabla time" in {
      val a = new Annotated
      for(_ <- 1 to 100) {
        a.time()
      }
      val snapshot = takeSnapshotOf("user-metric", "user-metric")
      snapshot.histogram("my-time").get.numberOfMeasurements should be (100)
    }
  }
}

class Annotated(val a:Int = 1) {

  @Trace("greeting")
  def greeting():Unit ={}

  @Trace("greeting2")
  @Segment(name="bla", category = "baba", library = "afasf")
  def greeting2():Unit ={}

  @Count(name = "#{ 'bla:' += this.a}", tags = """#{{'a':'b'}}""")
  def count():Unit = {}

  @Count(name = "#{this.a}", tags = """#{{'a':'b', 'c','c'}}""")
  def countMin():Unit = {}

//  @Segment(name="bla2", category = "baba", library = "afasf")
  @Count(name = "my-counter")
  def count2():Unit = {}

//  @Segment(name="bla3", category = "baba", library = "afasf")
  @MinMaxCount(name = "min-max-counter")
  def count3():Unit = {}

  @Time(name = "my-time")
  def time():Unit = {}

  @Histogram(name = "histogram")
  def getSomeValue():Long = 15
}