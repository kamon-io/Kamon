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

