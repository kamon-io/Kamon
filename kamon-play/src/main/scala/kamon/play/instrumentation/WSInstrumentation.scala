/* ===================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

import javax.net.ssl.SSLContext
import org.aspectj.lang.annotation.{ Around, Pointcut, Aspect }
import org.aspectj.lang.ProceedingJoinPoint
import com.ning.http.client._
import com.ning.http.client.filter.{ RequestFilter, FilterContext }
import kamon.trace.{ SegmentCompletionHandle, TraceRecorder }
import kamon.metrics.TraceMetrics.HttpClientRequest

@Aspect
class WSInstrumentation {

  @Pointcut("call(* play.api.libs.ws.WS$.newClient(..))")
  def onNewAsyncHttpClient(): Unit = {}

  @Around("onNewAsyncHttpClient()")
  def aroundNewAsyncHttpClient(pjp: ProceedingJoinPoint): Any = {
    val playConfig = play.api.Play.maybeApplication.map(_.configuration)
    val wsTimeout = playConfig.flatMap(_.getMilliseconds("ws.timeout"))
    val asyncHttpConfig = new AsyncHttpClientConfig.Builder()
      .setConnectionTimeoutInMs(playConfig.flatMap(_.getMilliseconds("ws.timeout.connection")).orElse(wsTimeout).getOrElse(120000L).toInt)
      .setIdleConnectionTimeoutInMs(playConfig.flatMap(_.getMilliseconds("ws.timeout.idle")).orElse(wsTimeout).getOrElse(120000L).toInt)
      .setRequestTimeoutInMs(playConfig.flatMap(_.getMilliseconds("ws.timeout.request")).getOrElse(120000L).toInt)
      .setFollowRedirects(playConfig.flatMap(_.getBoolean("ws.followRedirects")).getOrElse(true))
      .setUseProxyProperties(playConfig.flatMap(_.getBoolean("ws.useProxyProperties")).getOrElse(true))

    playConfig.flatMap(_.getString("ws.useragent")).map { useragent ⇒
      asyncHttpConfig.setUserAgent(useragent)
    }
    if (!playConfig.flatMap(_.getBoolean("ws.acceptAnyCertificate")).getOrElse(false)) {
      asyncHttpConfig.setSSLContext(SSLContext.getDefault)
    }

    asyncHttpConfig.addRequestFilter(new KamonRequestFilter())

    new AsyncHttpClient(asyncHttpConfig.build())
  }
}

class KamonRequestFilter extends RequestFilter {
  import KamonRequestFilter._

  override def filter(ctx: FilterContext[_]): FilterContext[_] = {
    val completionHandle = TraceRecorder.startSegment(HttpClientRequest(ctx.getRequest.getRawURI.toString(), UserTime), basicRequestAttributes(ctx.getRequest()))
    new FilterContext.FilterContextBuilder(ctx).asyncHandler(new AsyncHandlerWrapper[Response](ctx.getAsyncHandler(), completionHandle)).build()
  }

  class AsyncHandlerWrapper[T](asyncHandler: AsyncHandler[_], completionHandle: Option[SegmentCompletionHandle]) extends AsyncCompletionHandler[T] {
    override def onCompleted(response: Response): T = {
      completionHandle.map(_.finish(Map.empty))
      asyncHandler.onCompleted().asInstanceOf[T]
    }
    override def onThrowable(t: Throwable) = {
      asyncHandler.onThrowable(t)
    }
  }
}

object KamonRequestFilter {
  val UserTime = "UserTime"

  def basicRequestAttributes(request: Request): Map[String, String] = {
    Map[String, String](
      "host" -> request.getHeaders().getFirstValue("host"),
      "path" -> request.getURI.getPath,
      "method" -> request.getMethod)
  }
}

