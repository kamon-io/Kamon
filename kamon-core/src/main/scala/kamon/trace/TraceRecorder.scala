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

import akka.remote.instrumentation.TraceContextAwareWireFormats.RemoteTraceContext

import scala.language.experimental.macros
import java.util.concurrent.atomic.AtomicLong
import kamon.macros.InlineTraceContextMacro

import scala.util.Try
import java.net.InetAddress
import akka.actor.ActorSystem
import kamon.trace.TraceContext.SegmentIdentity

object TraceRecorder {
  private val traceContextStorage = new ThreadLocal[Option[TraceContext]] {
    override def initialValue(): Option[TraceContext] = None
  }

  private val tokenCounter = new AtomicLong
  private val hostnamePrefix = Try(InetAddress.getLocalHost.getHostName).getOrElse("unknown-localhost")

  def newToken = "%s-%s".format(hostnamePrefix, tokenCounter.incrementAndGet())

  private def newTraceContext(name: String, token: Option[String], metadata: Map[String, String],
    system: ActorSystem): TraceContext = {

    // In the future this should select between implementations.
    val finalToken = token.getOrElse(newToken)
    new SimpleMetricCollectionContext(name, finalToken, metadata, TraceContextOrigin.Local, system)
  }

  def joinRemoteTraceContext(remoteTraceContext: RemoteTraceContext, system: ActorSystem): TraceContext = {
    new SimpleMetricCollectionContext(
      remoteTraceContext.getTraceName(),
      remoteTraceContext.getTraceToken(),
      Map.empty,
      TraceContextOrigin.Remote,
      system,
      remoteTraceContext.getStartMilliTime(),
      remoteTraceContext.getIsOpen())
  }

  def forkTraceContext(context: TraceContext, newName: String): TraceContext = {
    new SimpleMetricCollectionContext(
      newName,
      context.token,
      Map.empty,
      TraceContextOrigin.Local,
      context.system)
  }

  def setContext(context: Option[TraceContext]): Unit = traceContextStorage.set(context)

  def clearContext: Unit = traceContextStorage.set(None)

  def currentContext: Option[TraceContext] = traceContextStorage.get()

  def start(name: String, token: Option[String] = None, metadata: Map[String, String] = Map.empty)(implicit system: ActorSystem) = {
    val ctx = newTraceContext(name, token, metadata, system)
    traceContextStorage.set(Some(ctx))
  }

  def startSegment(identity: SegmentIdentity, metadata: Map[String, String] = Map.empty): Option[SegmentCompletionHandle] =
    currentContext.map(_.startSegment(identity, metadata))

  def rename(name: String): Unit = currentContext.map(_.rename(name))

  def withNewTraceContext[T](name: String, token: Option[String] = None, metadata: Map[String, String] = Map.empty)(thunk: ⇒ T)(implicit system: ActorSystem): T =
    withTraceContext(Some(newTraceContext(name, token, metadata, system)))(thunk)

  def withTraceContext[T](context: Option[TraceContext])(thunk: ⇒ T): T = {
    val oldContext = currentContext
    setContext(context)

    try thunk finally setContext(oldContext)
  }

  def withInlineTraceContextReplacement[T](traceCtx: Option[TraceContext])(thunk: ⇒ T): T = macro InlineTraceContextMacro.withInlineTraceContextImpl[T, Option[TraceContext]]

  def finish(metadata: Map[String, String] = Map.empty): Unit = currentContext.map(_.finish(metadata))

}
