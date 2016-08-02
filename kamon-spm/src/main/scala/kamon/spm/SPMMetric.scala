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

package kamon.spm

import java.net.InetAddress
import java.util

import com.sematext.spm.client.tracing.thrift.{ TCall, TTracingEventType, _ }
import kamon.metric.instrument._
import kamon.util.MilliTimestamp

import scala.util.Random

case class SPMMetric(ts: MilliTimestamp, category: String, name: String, instrumentName: String, unitOfMeasurement: UnitOfMeasurement, snapshot: InstrumentSnapshot)

object SPMMetric {

  private val TraceDurationStoringThreshold = 30

  private def convert(unit: UnitOfMeasurement, value: Long): Long = unit match {
    case t: Time   ⇒ t.scale(Time.Milliseconds)(value).toLong
    case m: Memory ⇒ m.scale(Memory.Bytes)(value).toLong
    case _         ⇒ value
  }

  private def prefix(metric: SPMMetric): String = {
    s"${metric.ts.millis}\t${metric.category}-${metric.instrumentName}\t${metric.ts.millis}\t${metric.name}"
  }

  def format(metric: SPMMetric): String = metric match {
    case SPMMetric(_, _, _, _, unit, histo: Histogram#SnapshotType) ⇒ {
      val min = convert(unit, histo.min)
      val max = convert(unit, histo.max)
      val sum = convert(unit, histo.sum)
      s"${prefix(metric)}\t${min}\t${max}\t${sum}\t${histo.numberOfMeasurements}"
    }
    case SPMMetric(_, _, _, _, unit, counter: Counter#SnapshotType) ⇒ {
      s"${prefix(metric)}\t${counter.count}"
    }
  }

  def traceFormat(metric: SPMMetric, token: String): Array[Byte] = metric match {
    case SPMMetric(_, _, _, _, unit, histo: Histogram#SnapshotType) ⇒ {
      val event = new TTracingEvent()
      val thrift = new TPartialTransaction()
      thrift.setCallId(Random.nextLong)
      thrift.setParentCallId(0L)
      thrift.setTraceId(Random.nextLong)
      thrift.setRequest(metric.name)
      thrift.setStartTimestamp(metric.ts.millis)
      thrift.setEndTimestamp(metric.ts.millis + histo.max)
      thrift.setDuration(convert(unit, histo.max))
      thrift.setToken(token)
      thrift.setFailed(false)
      thrift.setEntryPoint(true)
      thrift.setAsynchronous(false)
      thrift.setTransactionType(TTransactionType.WEB)
      val summary = new TWebTransactionSummary()
      summary.setRequest(metric.name)
      thrift.setTransactionSummary(ThriftUtils.binaryProtocolSerializer().serialize(summary))
      val endpoint = new TEndpoint()
      endpoint.setHostname(InetAddress.getLocalHost.getHostName)
      endpoint.setAddress(InetAddress.getLocalHost.getAddress.toString)
      thrift.setEndpoint(endpoint)
      thrift.setCalls(new util.ArrayList[TCall]())
      thrift.setParameters(new util.HashMap[String, String]())
      event.setPartialTransaction(thrift)
      event.eventType = TTracingEventType.PARTIAL_TRANSACTION
      ThriftUtils.binaryProtocolSerializer().serialize(event)
    }
  }

  def isTraceToStore(metric: SPMMetric): Boolean = metric match {
    case SPMMetric(_, _, _, _, unit, histo: Histogram#SnapshotType) ⇒ {
      convert(unit, histo.max) > TraceDurationStoringThreshold
    }
  }
}
