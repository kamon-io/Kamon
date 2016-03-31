/* =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

package kamon.elasticsearch.instrumentation

import java.util.concurrent.TimeUnit.{ NANOSECONDS ⇒ nanos }

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Pointcut
import org.elasticsearch.action._
import org.slf4j.LoggerFactory

import kamon.Kamon
import kamon.elasticsearch.ElasticsearchExtension
import kamon.elasticsearch.metric.RequestsMetrics
import kamon.trace.SegmentCategory
import kamon.trace.TraceContext
import kamon.trace.Tracer

@Aspect
class RequestInstrumentation {

  import RequestInstrumentation._

  @Pointcut("execution(* org.elasticsearch.client.ElasticsearchClient.execute*(..)) && args(action, request, listener)")
  def onExecuteListener[Request <: ActionRequest[Request], Response <: ActionResponse, RequestBuilder <: ActionRequestBuilder[Request, Response, RequestBuilder]](action: Action[Request, Response, RequestBuilder], request: Request, listener: ActionListener[Response]): Unit = {}

  @Around("onExecuteListener(action, request, listener) ")
  def aroundExecuteListener[Request <: ActionRequest[Request], Response <: ActionResponse, RequestBuilder <: ActionRequestBuilder[Request, Response, RequestBuilder]](pjp: ProceedingJoinPoint, action: Action[Request, Response, RequestBuilder], request: Request, listener: ActionListener[Response]): Unit = {
    Tracer.currentContext.collect { ctx ⇒
      implicit val requestRecorder = Kamon.metrics.entity(RequestsMetrics, "elasticsearch-requests")
      val segment = generateSegment(ctx, request)
      val start = System.nanoTime()

      pjp.proceed(Array(action, request, new ActionListener[Response] {
        def onFailure(e: Throwable): Unit = { requestRecorder.errors.increment(); segment.finish(); listener.onFailure(e) }
        def onResponse(response: Response): Unit = { recordTrace(request, response, start); segment.finish(); listener.onResponse(response) }
      }))

    }
  } getOrElse pjp.proceed()

  def recordTrace[Request <: ActionRequest[Request], Response <: ActionResponse](request: Request, response: Response, start: Long)(implicit requestRecorder: RequestsMetrics) {
    val timeSpent = System.nanoTime() - start
    request match {
      case r: get.GetRequest       ⇒ requestRecorder.reads.record(timeSpent)
      case r: search.SearchRequest ⇒ requestRecorder.reads.record(timeSpent)
      case r: index.IndexRequest   ⇒ requestRecorder.writes.record(timeSpent)
      case r: update.UpdateRequest ⇒ requestRecorder.writes.record(timeSpent)
      case r: delete.DeleteRequest ⇒ requestRecorder.writes.record(timeSpent)
      case _ ⇒
        log.debug(s"Unable to parse request [$request]")
    }

    val timeSpentInMillis = nanos.toMillis(timeSpent)

    if (timeSpentInMillis >= ElasticsearchExtension.slowQueryThreshold) {
      requestRecorder.slows.increment()
      ElasticsearchExtension.processSlowQuery(request, timeSpentInMillis)
    }
  }

  def generateSegment(ctx: TraceContext, request: ActionRequest[_]) = {
    val segmentName = ElasticsearchExtension.generateElasticsearchSegmentName(request)
    val segment = ctx.startSegment(segmentName, SegmentCategory.Database, ElasticsearchExtension.SegmentLibraryName)
    segment
  }
}

object RequestInstrumentation {
  val log = LoggerFactory.getLogger(classOf[RequestInstrumentation])
}
