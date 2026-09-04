/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon
package testkit

import kamon.metric.{Instrument, Metric}
import kamon.tag.{Tag, TagSet}
import kamon.tag.Lookups._

import scala.collection.concurrent.TrieMap

/**
  * Utility functions to extract tags and instrument information from metrics. These utilities are only meant to be used
  * for testing purposes.
  */
object MetricInspection {

  /**
    * Returns all values for a given tag across all instruments of the inspected metric.
    */
  def tagValues[Inst <: Instrument[Inst, Sett], Sett <: Metric.Settings](
    metric: Metric[Inst, Sett],
    key: String
  ): Seq[String] = {
    instrumentsMap(metric).keys
      .map(t => t.get(coerce(key, "")))
      .filter(_.nonEmpty)
      .toSeq
  }

  private def extractInstrumentFromEntry[Inst](e: Any): Inst =
    Reflection.getFieldFromClass[Inst](e, "kamon.metric.Metric$BaseMetric$InstrumentEntry", "instrument")

  /**
    * Returns all instruments currently registered for the inspected metric.
    */
  def instruments[Inst <: Instrument[Inst, Sett], Sett <: Metric.Settings](metric: Metric[Inst, Sett])
    : Map[TagSet, Inst] = {
    instrumentsMap(metric)
      .mapValues(extractInstrumentFromEntry[Inst])
      .toMap
  }

  /**
    * Returns all instruments that contain at least the provided tags for the inspected metric.
    */
  def instruments[Inst <: Instrument[Inst, Sett], Sett <: Metric.Settings](
    metric: Metric[Inst, Sett],
    tags: TagSet
  ): Map[TagSet, Inst] = {
    def hasAllRequestedTags(instrumentTags: TagSet): Boolean = {
      val instrumentPairs = instrumentTags.iterator().map(t => (t.key -> Tag.unwrapValue(t))).toMap
      tags.iterator().forall(t => instrumentPairs.get(t.key).exists(sv => sv == Tag.unwrapValue(t)))
    }

    instrumentsMap(metric)
      .filterKeys(hasAllRequestedTags)
      .mapValues(extractInstrumentFromEntry[Inst])
      .toMap
  }

  private def instrumentsMap[Inst <: Instrument[Inst, Sett], Sett <: Metric.Settings](metric: Metric[Inst, Sett])
    : TrieMap[TagSet, Any] = {
    Reflection.getFieldFromClass[TrieMap[TagSet, Any]](metric, "kamon.metric.Metric$BaseMetric", "_instruments")
      .filter { case (_, entry) =>
        !Reflection.getFieldFromClass[Boolean](
          entry,
          "kamon.metric.Metric$BaseMetric$InstrumentEntry",
          "removeOnNextSnapshot"
        )
      }
  }

  /**
    * Exposes an implicitly available syntax for inspecting tags and instruments from a metric.
    */
  trait Syntax {

    trait RichMetric[Inst <: Instrument[Inst, Sett], Sett <: Metric.Settings] {
      def tagValues(key: String): Seq[String]
      def instruments(): Map[TagSet, Inst]
      def instruments(tags: TagSet): Map[TagSet, Inst]
    }

    implicit def metricInspectionSyntax[Inst <: Instrument[Inst, Sett], Sett <: Metric.Settings](metric: Metric[
      Inst,
      Sett
    ]): RichMetric[Inst, Sett] =
      new RichMetric[Inst, Sett] {

        def tagValues(key: String): Seq[String] =
          MetricInspection.tagValues(metric, key)

        def instruments(): Map[TagSet, Inst] =
          MetricInspection.instruments(metric)

        def instruments(tags: TagSet): Map[TagSet, Inst] =
          MetricInspection.instruments(metric, tags)
      }
  }

  object Syntax extends Syntax
}
