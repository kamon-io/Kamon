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
package kamon.util

import java.util.function.Supplier

import kamon.trace.{SpanContext => KamonSpanContext}
import kamon.Kamon
import org.slf4j.MDC

import scala.collection.JavaConverters._
//
//object BaggageOnMDC {
//
//  /**
//    * Copy all baggage keys into SLF4J's MDC, evaluates the provided piece of code and removes the baggage keys
//    * afterwards, only when there is a currently active span. Optionally adds the Trace ID as well.
//    *
//    */
//  def withBaggageOnMDC[T](includeTraceID: Boolean, code: => T): T = {
//    val activeSpan = Kamon.activeSpan()
//    if(activeSpan == null)
//      code
//    else {
//      val baggageItems = activeSpan.context().baggageItems().asScala
//      baggageItems.foreach(entry => MDC.put(entry.getKey, entry.getValue))
//      if(includeTraceID)
//        addTraceIDToMDC(activeSpan.context())
//
//      val evaluatedCode = code
//
//      baggageItems.foreach(entry => MDC.remove(entry.getKey))
//      if(includeTraceID)
//        removeTraceIDFromMDC()
//
//      evaluatedCode
//
//    }
//  }
//
//  def withBaggageOnMDC[T](code: Supplier[T]): T =
//    withBaggageOnMDC(true, code.get())
//
//  def withBaggageOnMDC[T](includeTraceID: Boolean, code: Supplier[T]): T =
//    withBaggageOnMDC(includeTraceID, code.get())
//
//  def withBaggageOnMDC[T](code: => T): T =
//    withBaggageOnMDC(true, code)
//
//  private val TraceIDKey = "trace_id"
//
//  private def addTraceIDToMDC(context: io.opentracing.SpanContext): Unit = context match {
//    case ctx: KamonSpanContext => MDC.put(TraceIDKey, HexCodec.toLowerHex(ctx.traceID))
//    case _ =>
//  }
//
//  private def removeTraceIDFromMDC(): Unit =
//    MDC.remove(TraceIDKey)
//}
