/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon.play.instrumentation

import kamon.Kamon
import kamon.play.OperationNameFilter
import kamon.trace.Span
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.{AfterThrowing, _}
import org.jboss.netty.channel.{ChannelHandlerContext, MessageEvent}
import org.jboss.netty.handler.codec.http.DefaultHttpResponse
import play.api.mvc._

@Aspect
class RequestHandlerInstrumentation {

  private lazy val filter: EssentialFilter = new OperationNameFilter()

  @Around("execution(* org.jboss.netty.handler.codec.http.HttpMessageDecoder.decode(..)) && args(ctx, *, *, *)")
  def onDecodeRequest(pjp: ProceedingJoinPoint, ctx: ChannelHandlerContext): AnyRef = {
    val result = pjp.proceed()
    if(result.isHttpRequest()) {
      val request = result.toHttpRequest()
      val channel = ctx.getChannel.toContextAware()
      val incomingContext = decodeContext(request)
      val serverSpan = Kamon.buildSpan("unknown-operation")
        .asChildOf(incomingContext.get(Span.ContextKey))
        .withMetricTag("span.kind", "server")
        .withMetricTag("component", "play.server.netty")
        .withMetricTag("http.method", request.getMethod.getName)
        .withTag("http.url", request.getUri)
        .start()

      channel.setContext(incomingContext.withKey(Span.ContextKey, serverSpan))
    }
    result
  }

  @After("execution(* org.jboss.netty.handler.codec.http.HttpMessageEncoder.encode(..)) && args(ctx, *, response)")
  def onEncodeResponse(ctx: ChannelHandlerContext, response: DefaultHttpResponse): Unit = {
    val responseStatus = response.getStatus
    val serverSpan = ctx.getChannel.getContext().get(Span.ContextKey)
    serverSpan.tag("http.status_code", responseStatus.getCode)

    if(isError(responseStatus.getCode))
      serverSpan.addError(responseStatus.getReasonPhrase)

    if(responseStatus.getCode == StatusCodes.NotFound)
      serverSpan.setOperationName("not-found")

    serverSpan.finish()
  }

  @AfterThrowing(pointcut = "execution(* org.jboss.netty.handler.codec.http.HttpMessageEncoder.encode(..)) && args(ctx, *, response)",  throwing = "error")
  def onEncodeResponseError(ctx: ChannelHandlerContext, response: DefaultHttpResponse,  error:Throwable): Unit = {
    val serverSpan = ctx.getChannel.getContext().get(Span.ContextKey)
    serverSpan.addError("error.object", error)
    serverSpan.finish()
  }

  @Around("execution(* play.core.server.netty.PlayDefaultUpstreamHandler.messageReceived(..)) && args(ctx, event)")
  def onMessageReceived(pjp: ProceedingJoinPoint, ctx: ChannelHandlerContext, event: MessageEvent): AnyRef = {
    if(event.getMessage.isHttpRequest())
      Kamon.withContext(ctx.getChannel.toContextAware().getContext)(pjp.proceed())
    else
      pjp.proceed()
  }

  @Around("call(* play.api.http.HttpFilters.filters(..))")
  def filters(pjp: ProceedingJoinPoint): Any = {
    filter +: pjp.proceed().asInstanceOf[Seq[EssentialFilter]]
  }
}
