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

package kamon.trace

import kamon.{ MilliTimestamp, RelativeNanoTimestamp, Kamon }

import scala.language.experimental.macros
import java.util.concurrent.atomic.AtomicLong
import kamon.macros.InlineTraceContextMacro

import scala.util.Try
import java.net.InetAddress
import akka.actor.ActorSystem

object TraceRecorder {
  private val traceContextStorage = new ThreadLocal[TraceContext] {
    override def initialValue(): TraceContext = EmptyTraceContext
  }

  private val tokenCounter = new AtomicLong
  private val hostnamePrefix = Try(InetAddress.getLocalHost.getHostName).getOrElse("unknown-localhost")

  def newToken: String = hostnamePrefix + "-" + String.valueOf(tokenCounter.incrementAndGet())

  private def newTraceContext(name: String, token: Option[String], system: ActorSystem): TraceContext =
    Kamon(Trace)(system).newTraceContext(name, token.getOrElse(newToken), TraceContextOrigin.Local, system)

  def joinRemoteTraceContext(traceName: String, traceToken: String, startTimestamp: MilliTimestamp, isOpen: Boolean, system: ActorSystem): TraceContext = {
    val equivalentStartTimestamp = RelativeNanoTimestamp.relativeTo(startTimestamp)
    Kamon(Trace)(system).newTraceContext(traceName, traceToken, isOpen, TraceContextOrigin.Remote, equivalentStartTimestamp, system)
  }

  def setContext(context: TraceContext): Unit = traceContextStorage.set(context)

  def clearContext: Unit = traceContextStorage.set(EmptyTraceContext)

  def currentContext: TraceContext = traceContextStorage.get()

  def start(name: String, token: Option[String] = None)(implicit system: ActorSystem) = {
    val ctx = newTraceContext(name, token, system)
    traceContextStorage.set(ctx)
  }

  def rename(name: String): Unit = currentContext.rename(name)

  def withNewTraceContext[T](name: String, token: Option[String] = None)(thunk: ⇒ T)(implicit system: ActorSystem): T =
    withTraceContext(newTraceContext(name, token, system))(thunk)

  def withTraceContext[T](context: TraceContext)(thunk: ⇒ T): T = {
    val oldContext = currentContext
    setContext(context)

    try thunk finally setContext(oldContext)
  }

  def withTraceContextAndSystem[T](thunk: (TraceContext, ActorSystem) ⇒ T): Option[T] = currentContext match {
    case ctx: MetricsOnlyContext ⇒ Some(thunk(ctx, ctx.system))
    case EmptyTraceContext       ⇒ None
  }

  def withInlineTraceContextReplacement[T](traceCtx: TraceContext)(thunk: ⇒ T): T = macro InlineTraceContextMacro.withInlineTraceContextImpl[T, TraceContext]

  def finish(): Unit = currentContext.finish()

}
