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

case class SPMMetric(ts: MilliTimestamp, category: String, name: String, instrumentName: String, tags: Map[String, String], unitOfMeasurement: UnitOfMeasurement, snapshot: InstrumentSnapshot)

object SPMMetric {

  private def convert(unit: UnitOfMeasurement, value: Long): Long = unit match {
    case t: Time   ⇒ t.scale(Time.Milliseconds)(value).toLong
    case m: Memory ⇒ m.scale(Memory.Bytes)(value).toLong
    case _         ⇒ value
  }

  private def prefix(metric: SPMMetric): String = {
    s"${metric.ts.millis}\t${metric.category}-${metric.instrumentName}\t${metric.ts.millis}\t${metric.name}"
  }

  private def httpRequestPrefix(metric: SPMMetric): String = {
    val code = metric.instrumentName.substring(metric.instrumentName.lastIndexOf('_') + 1)
    val requestName = metric.instrumentName.substring(0, metric.instrumentName.lastIndexOf('_'))
    if (code.matches("[4]\\d\\d")) {
      s"${metric.ts.millis}\t${"http-4XX-response"}\t${metric.ts.millis}\t${requestName}"
    } else if (code.matches("[5]\\d\\d")) {
      s"${metric.ts.millis}\t${"http-5XX-response"}\t${metric.ts.millis}\t${requestName}"
    } else {
      s"${metric.ts.millis}\t${"http-calls"}\t${metric.ts.millis}\t${requestName}"
    }
  }

  def format(metric: SPMMetric): String = metric match {
    case SPMMetric(_, "http-server", _, name, _, unit, counter: Counter#SnapshotType) ⇒ {
      s"${httpRequestPrefix(metric)}\t${counter.count}"
    }
    case SPMMetric(_, _, _, _, _, unit, histo: Histogram#SnapshotType) ⇒ {
      val min = convert(unit, histo.min)
      val max = convert(unit, histo.max)
      val sum = convert(unit, histo.sum)
      val p50 = convert(unit, histo.percentile(50D))
      val p90 = convert(unit, histo.percentile(90D))
      val p95 = convert(unit, histo.percentile(95D))
      val p99 = convert(unit, histo.percentile(99D))
      val p995 = convert(unit, histo.percentile(99.5D))
      s"${prefix(metric)}\t${min}\t${max}\t${sum}\t${histo.numberOfMeasurements}\t${p50}\t${p90}\t${p95}\t${p99}\t${p995}"
    }
    case SPMMetric(_, _, _, _, _, unit, counter: Counter#SnapshotType) ⇒ {
      s"${prefix(metric)}\t${counter.count}"
    }
  }

  def traceFormat(metric: SPMMetric, token: String, segments: List[SPMMetric]): Array[Byte] = metric match {
    case SPMMetric(_, _, _, _, tags, unit, histo: Histogram#SnapshotType) ⇒ {
      val tracingMetrics = segments.filter(segments ⇒ (segments.tags.get("trace") == metric.name))
      val event = new TTracingEvent()
      val thrift = new TPartialTransaction()
      val rnd = new Random()
      val callId = rnd.nextLong()
      thrift.setParentCallId(0)
      thrift.setCallId(callId)
      thrift.setTraceId(rnd.nextLong())
      val calls = new util.ArrayList[TCall]()
      val tCall = new TCall()
      tCall.setDuration(convert(unit, histo.max))
      tCall.setStartTimestamp(metric.ts.millis)
      tCall.setEndTimestamp(metric.ts.millis + convert(unit, histo.max))
      tCall.setCallId(callId)
      tCall.setParentCallId(0)
      tCall.setSignature(metric.name)
      calls.add(tCall)
      segments.foreach((segment: SPMMetric) ⇒ {
        segment match {
          case SPMMetric(_, _, _, _, tags, unit, histo: Histogram#SnapshotType) ⇒ {
            val tcall = new TCall()
            tcall.setDuration(convert(unit, histo.max))
            tcall.setStartTimestamp(segment.ts.millis)
            tcall.setEndTimestamp(segment.ts.millis + convert(unit, histo.max))
            tcall.setCallId(rnd.nextLong())
            tcall.setParentCallId(callId)
            tcall.setSignature(segment.name)
            calls.add(tcall)
          }
          case others ⇒
        }
      })
      thrift.setRequest(metric.name)
      thrift.setStartTimestamp(metric.ts.millis)
      thrift.setEndTimestamp(metric.ts.millis + convert(unit, histo.max))
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
      endpoint.setAddress(InetAddress.getLocalHost.getHostAddress)
      thrift.setEndpoint(endpoint)
      thrift.setCalls(calls)
      thrift.setParameters(new util.HashMap[String, String]())
      event.setPartialTransaction(thrift)
      event.eventType = TTracingEventType.PARTIAL_TRANSACTION
      ThriftUtils.binaryProtocolSerializer().serialize(event)
    }
    case SPMMetric(_, _, _, _, tags, unit, counter: Counter#SnapshotType) ⇒ {
      val event = new TTracingEvent()
      val thrift = new TPartialTransaction()
      val calls = new util.ArrayList[TCall]()
      val rnd = new Random()
      val callId = rnd.nextLong()
      val tCall = new TCall()
      tCall.setDuration(0L)
      tCall.setStartTimestamp(metric.ts.millis)
      tCall.setEndTimestamp(metric.ts.millis)
      tCall.setCallId(callId)
      tCall.setParentCallId(0)
      tCall.setSignature(metric.name)
      calls.add(tCall)
      thrift.setCallId(callId)
      thrift.setParentCallId(0L)
      thrift.setTraceId(Random.nextLong)
      thrift.setRequest(metric.name)
      thrift.setStartTimestamp(metric.ts.millis)
      thrift.setEndTimestamp(metric.ts.millis)
      thrift.setDuration(0L)
      thrift.setToken(token)
      thrift.setFailed(true)
      thrift.setEntryPoint(true)
      thrift.setAsynchronous(false)
      thrift.setTransactionType(TTransactionType.WEB)
      val summary = new TWebTransactionSummary()
      summary.setRequest(metric.name)
      thrift.setTransactionSummary(ThriftUtils.binaryProtocolSerializer().serialize(summary))
      val endpoint = new TEndpoint()
      endpoint.setHostname(InetAddress.getLocalHost.getHostName)
      endpoint.setAddress(InetAddress.getLocalHost.getHostAddress)
      thrift.setEndpoint(endpoint)
      thrift.setCalls(calls)
      thrift.setParameters(new util.HashMap[String, String]())
      event.setPartialTransaction(thrift)
      event.eventType = TTracingEventType.PARTIAL_TRANSACTION
      ThriftUtils.binaryProtocolSerializer().serialize(event)
    }
  }

  def isTraceToStore(metric: SPMMetric, threshold: Int): Boolean = metric match {
    case SPMMetric(_, _, _, _, _, unit, histo: Histogram#SnapshotType) ⇒ {
      convert(unit, histo.max) > threshold
    }
    case SPMMetric(_, _, _, _, _, unit, counter: Counter#SnapshotType) ⇒ {
      metric.instrumentName.equals("errors") && counter.count > 0
    }
    case others ⇒ {
      false
    }
  }
}
