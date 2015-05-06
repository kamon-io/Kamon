/*
 * =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
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

package kamon.newrelic

import kamon.metric.instrument.{ CollectionContext, InstrumentSnapshot }
import kamon.metric.{ Entity, EntitySnapshot }

/**
 * @since 21.04.2015
 */
object AkkaMetricExtractor extends MetricExtractor {

  private val router = "akka-router"
  private val actor = "akka-actor"
  private val dispatcher = "akka-dispatcher"
  private val akkaCategories = actor :: router :: dispatcher :: Nil

  def extract(settings: AgentSettings, collectionContext: CollectionContext, metrics: Map[Entity, EntitySnapshot]): Map[MetricID, MetricData] = {

    def toNewRelicMetric(entity: Entity, snapshot: EntitySnapshot) = entity.category match {
      case `actor`      ⇒ logActorMetrics(entity.name, snapshot, actor)
      case `dispatcher` ⇒ logDispatcherMetrics(entity, snapshot)
      // TODO case `router` ⇒ logRouterMetrics(entity.name, snapshot)
    }

    val akka = metrics filter akkaMetrics
    val result = akka flatMap { case (entity, snapshot) ⇒ toNewRelicMetric(entity, snapshot) }
    result
  }

  def logDispatcherMetrics(entity: Entity, snapshot: EntitySnapshot): Iterable[Metric] =
    entity.tags get "dispatcher-type" match {
      case Some("fork-join-pool")       ⇒ logForkJoinPool(entity.name, snapshot, "akka-fork-join-pool")
      case Some("thread-pool-executor") ⇒ logThreadPoolExecutor(entity.name, snapshot, "akka-thread-pool-executor")
      case ignoreOthers                 ⇒ Nil
    }

  def logActorMetrics = logMetrics(actorMetrics) _
  def logForkJoinPool = logMetrics(forkJoinMetrics) _
  def logThreadPoolExecutor = logMetrics(threadPoolMetrics) _

  def actorMetrics(snapshot: EntitySnapshot) =
    ("processing-time", snapshot.histogram _) ::
      ("time-in-mailbox", snapshot.histogram _) ::
      ("mailbox-size", snapshot.minMaxCounter _) ::
      ("errors", snapshot.counter _) ::
      Nil

  def forkJoinMetrics(snapshot: EntitySnapshot) =
    ("parallelism", snapshot.minMaxCounter _) ::
      ("pool-size", snapshot.gauge _) ::
      ("active-threads", snapshot.gauge _) ::
      ("running-threads", snapshot.gauge _) ::
      ("queued-task-count", snapshot.gauge _) ::
      Nil

  def threadPoolMetrics(snapshot: EntitySnapshot) =
    Seq("core-pool-size", "max-pool-size", "pool-size", "active-threads", "processed-tasks").
      zip(Vector.fill(5)(snapshot.gauge _))

  protected[newrelic] def akkaMetrics(kv: (Entity, EntitySnapshot)) = akkaCategories.contains(kv._1.category)

  protected[newrelic] def camelize(name: String) =
    name split "-" filter { _.nonEmpty } map { part ⇒ part.head.toUpper + part.tail.toLowerCase } mkString ""

  protected[newrelic]type Operation = String ⇒ Option[InstrumentSnapshot]

  protected[newrelic] def logMetrics(nameAndGetterPairs: EntitySnapshot ⇒ Seq[(String, Operation)])(actorName: String, actorSnapshot: EntitySnapshot, prefix: String): Iterable[Metric] =
    for {
      (name, method) ← nameAndGetterPairs(actorSnapshot)
      (key, instrumentSnapshot) ← actorSnapshot.metrics.find(_._1.name == name)
      metric ← method(name)
      metricName = camelize(name)
      prefixName = camelize(prefix)
    } yield Metric(metric, key.unitOfMeasurement, s"$prefixName/$actorName/$metricName", None)

}
