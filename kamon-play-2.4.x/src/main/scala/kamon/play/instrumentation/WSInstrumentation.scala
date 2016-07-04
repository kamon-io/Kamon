/* ===================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */

package kamon.play.instrumentation

import kamon.play.PlayExtension
import kamon.trace.{ Tracer, SegmentCategory }
import kamon.util.SameThreadExecutionContext
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.{ Around, Aspect, Pointcut }
import play.api.libs.ws.{ WSRequest, WSResponse }
import scala.concurrent.Future

@Aspect
class WSInstrumentation {

  @Pointcut("execution(* play.api.libs.ws.ning.NingWSRequest.execute()) && this(request)")
  def onExecuteRequest(request: WSRequest): Unit = {}

  @Around("onExecuteRequest(request)")
  def aroundExecuteRequest(pjp: ProceedingJoinPoint, request: WSRequest): Any = {
    Tracer.currentContext.collect { ctx ⇒
      val segmentName = PlayExtension.generateHttpClientSegmentName(request)
      val segment = ctx.startSegment(segmentName, SegmentCategory.HttpClient, PlayExtension.SegmentLibraryName)
      val response = pjp.proceed().asInstanceOf[Future[WSResponse]]

      response.onComplete(result ⇒ segment.finish())(SameThreadExecutionContext)
      response
    } getOrElse pjp.proceed()
  }
}