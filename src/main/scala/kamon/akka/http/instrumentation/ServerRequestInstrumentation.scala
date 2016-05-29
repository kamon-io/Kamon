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

package kamon.akka.http.instrumentation

import akka.NotUsed
import akka.event.LoggingAdapter
import akka.http.scaladsl.ConnectionContext
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.http.scaladsl.settings.ServerSettings
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.{ Around, Aspect }

@Aspect
class ServerRequestInstrumentation {

  @Around("execution(* akka.http.scaladsl.HttpExt.bindAndHandle(..)) && args(handler, interface, port, connectionContext, settings, log, materializer)")
  def onBindAndHandle(pjp: ProceedingJoinPoint,
    handler: Flow[HttpRequest, HttpResponse, Any],
    interface: String,
    port: AnyRef,
    connectionContext: ConnectionContext,
    settings: ServerSettings,
    log: LoggingAdapter,
    materializer: Materializer): AnyRef = {

    pjp.proceed(Array(FlowWrapper(handler.asInstanceOf[Flow[HttpRequest, HttpResponse, NotUsed]]), interface, port, connectionContext, settings, log, materializer))
  }
}