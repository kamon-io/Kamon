package kamon.instrumentation.akka.http

import java.util.concurrent.Callable
import akka.http.scaladsl.marshalling.{ToEntityMarshaller, ToResponseMarshallable, ToResponseMarshaller}
import akka.http.scaladsl.model.StatusCodes.Redirection
import akka.http.scaladsl.model.{HttpHeader, HttpRequest, HttpResponse, StatusCode, Uri}
import akka.http.scaladsl.server.PathMatcher.{Matched, Unmatched}
import akka.http.scaladsl.server.directives.{BasicDirectives, CompleteOrRecoverWithMagnet, OnSuccessMagnet}
import akka.http.scaladsl.server.directives.RouteDirectives.reject
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.util.Tupler
import akka.http.scaladsl.util.FastFuture
import kamon.Kamon
import kamon.instrumentation.akka.http.HasMatchingContext.PathMatchingContext
import kamon.instrumentation.context.{HasContext, InvokeWithCapturedContext}
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.api.instrumentation.mixin.Initializer
import kanela.agent.libs.net.bytebuddy.implementation.bind.annotation._

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}
import java.util.regex.Pattern
import akka.NotUsed
import akka.http.scaladsl.server.RouteResult.Rejected
import akka.stream.scaladsl.Flow
import kamon.context.Context
import kanela.agent.libs.net.bytebuddy.matcher.ElementMatchers.isPublic

import scala.collection.immutable

class AkkaHttpServerInstrumentation extends InstrumentationBuilder {

  /**
    * When instrumenting bindAndHandle what we do is wrap the Flow[HttpRequest, HttpResponse, NotUsed] provided by
    * the user and add all the processing there. This is the part of the instrumentation that performs Context
    * propagation, tracing and gather metrics using the HttpServerInstrumentation packed in common.
    *
    * One important point about the HTTP Server instrumentation is that because it is almost impossible to have a proper
    * operation name before the request processing hits the routing tree, we are delaying the sampling decision to the
    * point at which we have some operation name.
    */
  onType("akka.http.scaladsl.HttpExt")
    .advise(method("bindAndHandle"), classOf[HttpExtBindAndHandleAdvice])

  /**
    * For the HTTP/2 instrumentation, since the parts where we can capture the interface/port and the actual flow
    * creation happen at different times we are wrapping the handler with the interface/port data and reading that
    * information when turning the handler function into a flow and wrapping it the same way we would for HTTP/1.
    */
  onType("akka.http.scaladsl.Http2Ext")
    .advise(method("bindAndHandleAsync") and isPublic(), classOf[Http2ExtBindAndHandleAdvice])

  onType("akka.http.impl.engine.http2.Http2Blueprint$")
    .intercept(method("handleWithStreamIdHeader"), Http2BlueprintInterceptor)

  /**
    * The rest of these sections are just about making sure that we can generate an appropriate operation name (i.e. free
    * of variables) and take a Sampling Decision in case none has been taken so far.
    */
  onType("akka.http.scaladsl.server.RequestContextImpl")
    .mixin(classOf[HasMatchingContext.Mixin])
    .intercept(method("copy"), RequestContextCopyInterceptor)

  onType("akka.http.scaladsl.server.directives.PathDirectives$class")
    .intercept(method("rawPathPrefix"), classOf[PathDirectivesRawPathPrefixInterceptor])

  onType("akka.http.scaladsl.server.directives.FutureDirectives$class")
    .intercept(method("onComplete"), classOf[ResolveOperationNameOnRouteInterceptor])

  onTypes(
    "akka.http.scaladsl.server.directives.OnSuccessMagnet$",
    "akka.http.scaladsl.server.directives.CompleteOrRecoverWithMagnet$"
  )
    .intercept(method("apply"), classOf[ResolveOperationNameOnRouteInterceptor])

  onType("akka.http.scaladsl.server.directives.RouteDirectives$class")
    .intercept(method("complete"), classOf[ResolveOperationNameOnRouteInterceptor])
    .intercept(method("redirect"), classOf[ResolveOperationNameOnRouteInterceptor])
    .intercept(method("failWith"), classOf[ResolveOperationNameOnRouteInterceptor])

  /**
    * Support for HTTP/1 and HTTP/2 at the same time.
    */

  onType("akka.stream.scaladsl.Flow")
    .advise(method("mapAsync"), classOf[FlowOpsMapAsyncAdvice])

}

trait HasMatchingContext {
  def defaultOperationName: String
  def matchingContext: Seq[PathMatchingContext]
  def setMatchingContext(ctx: Seq[PathMatchingContext]): Unit
  def setDefaultOperationName(defaultOperationName: String): Unit
  def prependMatchingContext(matched: PathMatchingContext): Unit
  def popOneMatchingContext(): Unit
}

object HasMatchingContext {

  case class PathMatchingContext(
    fullPath: String,
    matched: Matched[_]
  )

  class Mixin(var matchingContext: Seq[PathMatchingContext], var defaultOperationName: String)
      extends HasMatchingContext {

    override def setMatchingContext(matchingContext: Seq[PathMatchingContext]): Unit =
      this.matchingContext = matchingContext

    override def setDefaultOperationName(defaultOperationName: String): Unit =
      this.defaultOperationName = defaultOperationName

    override def prependMatchingContext(matched: PathMatchingContext): Unit =
      matchingContext = matched +: matchingContext

    override def popOneMatchingContext(): Unit =
      matchingContext = matchingContext.tail

    @Initializer
    def initialize(): Unit =
      matchingContext = Seq.empty
  }
}

class ResolveOperationNameOnRouteInterceptor
object ResolveOperationNameOnRouteInterceptor {
  import akka.http.scaladsl.util.FastFuture._

  // We are replacing some of the basic directives here to ensure that we will resolve both the Sampling Decision and
  // the operation name before the request gets to the actual handling code (presumably inside of a "complete"
  // directive.

  def complete(@Argument(1) m: => ToResponseMarshallable): StandardRoute =
    StandardRoute(resolveOperationName(_).complete(m))

  def complete[T](status: StatusCode, v: => T)(implicit m: ToEntityMarshaller[T]): StandardRoute =
    StandardRoute(resolveOperationName(_).complete((status, v)))

  def complete[T](status: StatusCode, headers: immutable.Seq[HttpHeader], v: => T)(implicit
    m: ToEntityMarshaller[T]
  ): StandardRoute =
    complete((status, headers, v))

  def redirect(@Argument(1) uri: Uri, @Argument(2) redirectionType: Redirection): StandardRoute =
    StandardRoute(resolveOperationName(_).redirect(uri, redirectionType))

  def failWith(@Argument(1) error: Throwable): StandardRoute = {
    Kamon.currentSpan().fail(error)
    StandardRoute(resolveOperationName(_).fail(error))
  }

  def onComplete[T](@Argument(1) future: => Future[T]): Directive1[Try[T]] =
    Directive { inner => ctx =>
      import ctx.executionContext
      resolveOperationName(ctx)
      future.fast.transformWith(t => inner(Tuple1(t))(ctx))
    }

  def apply[T](future: => Future[T])(implicit tupler: Tupler[T]): OnSuccessMagnet { type Out = tupler.Out } =
    new OnSuccessMagnet {
      type Out = tupler.Out
      val directive = Directive[tupler.Out] { inner => ctx =>
        import ctx.executionContext
        resolveOperationName(ctx)
        future.fast.flatMap(t => inner(tupler(t))(ctx))
      }(tupler.OutIsTuple)
    }

  def apply[T](future: => Future[T])(implicit m: ToResponseMarshaller[T]): CompleteOrRecoverWithMagnet =
    new CompleteOrRecoverWithMagnet {
      val directive = Directive[Tuple1[Throwable]] { inner => ctx =>
        import ctx.executionContext
        resolveOperationName(ctx)
        future.fast.transformWith {
          case Success(res)   => ctx.complete(res)
          case Failure(error) => inner(Tuple1(error))(ctx)
        }
      }
    }

  private def resolveOperationName(requestContext: RequestContext): RequestContext = {

    // We will only change the operation name if the last edit made to it was an automatic one. At this point, the only
    // way in which the operation name might have changed is if the user changed it with the operationName directive or
    // by accessing the Span and changing it directly there, so we wouldn't want to overwrite that.

    Kamon.currentContext().get(LastAutomaticOperationNameEdit.Key).foreach(lastEdit => {
      val currentSpan = Kamon.currentSpan()

      if (lastEdit.allowAutomaticChanges) {
        if (currentSpan.operationName() == lastEdit.operationName) {
          val allMatches = requestContext.asInstanceOf[HasMatchingContext].matchingContext.reverse.map(singleMatch)
          val operationName = allMatches.mkString("")

          if (operationName.nonEmpty) {
            currentSpan
              .name(operationName)
              .takeSamplingDecision()

            lastEdit.operationName = operationName
          }
        } else {
          lastEdit.allowAutomaticChanges = false
        }
      } else {
        currentSpan.takeSamplingDecision()
      }
    })

    requestContext
  }

  private def singleMatch(matching: PathMatchingContext): String = {
    val rest = matching.matched.pathRest.toString()
    val consumedCount = matching.fullPath.length - rest.length
    val consumedSegment = matching.fullPath.substring(0, consumedCount)

    matching.matched.extractions match {
      case () => // string segment matched
        consumedSegment
      case tuple: Product =>
        val values = tuple.productIterator.toList map {
          case Some(x)    => List(x.toString)
          case None       => Nil
          case long: Long => List(long.toString, long.toHexString)
          case int: Int   => List(int.toString, int.toHexString)
          case a: Any     => List(a.toString)
        }
        values.flatten.fold(consumedSegment) { (full, value) =>
          val r = "(?i)(^|/)" + Pattern.quote(value) + "($|/)"
          full.replaceFirst(r, "$1{}$2")
        }
    }
  }
}

/**
  * Tracks the last operation name that was automatically assigned to an operation via instrumentation. The
  * instrumentation might assign a name to the operations via settings on the HTTP Server instrumentation instance or
  * via the Path directives instrumentation, but might never reassign a name if the user somehow assigned their own name
  * to the operation. Users chan change operation names by:
  *   - Using operation mappings via configuration of the HTTP Server.
  *   - Providing a custom HTTP Operation Name Generator for the server.
  *   - Using the "operationName" directive.
  *   - Directly accessing the Span for the current operation and changing the name on it.
  *
  */
class LastAutomaticOperationNameEdit(
  @volatile var operationName: String,
  @volatile var allowAutomaticChanges: Boolean
)

object LastAutomaticOperationNameEdit {
  val Key = Context.key[Option[LastAutomaticOperationNameEdit]]("laone", None)

  def apply(operationName: String, allowAutomaticChanges: Boolean): LastAutomaticOperationNameEdit =
    new LastAutomaticOperationNameEdit(operationName, allowAutomaticChanges)
}

object RequestContextCopyInterceptor {

  @RuntimeType
  def copy(@This context: RequestContext, @SuperCall copyCall: Callable[RequestContext]): RequestContext = {
    val copiedRequestContext = copyCall.call()
    copiedRequestContext.asInstanceOf[HasMatchingContext].setMatchingContext(
      context.asInstanceOf[HasMatchingContext].matchingContext
    )
    copiedRequestContext
  }
}

class PathDirectivesRawPathPrefixInterceptor
object PathDirectivesRawPathPrefixInterceptor {
  import BasicDirectives._

  @RuntimeType
  def rawPathPrefix[T](@Argument(1) matcher: PathMatcher[T]): Directive[T] = {
    implicit val LIsTuple = matcher.ev

    extract { ctx =>
      val fullPath = ctx.unmatchedPath.toString()
      val matching = matcher(ctx.unmatchedPath)

      matching match {
        case m: Matched[_] =>
          ctx.asInstanceOf[HasMatchingContext]
            .prependMatchingContext(PathMatchingContext(fullPath, m))
        case _ =>
      }

      (ctx, matching)
    } flatMap {
      case (ctx, Matched(rest, values)) =>
        tprovide(values) & mapRequestContext(_ withUnmatchedPath rest) & mapRouteResult { routeResult =>
          if (routeResult.isInstanceOf[Rejected])
            ctx.asInstanceOf[HasMatchingContext].popOneMatchingContext()

          routeResult
        }

      case (_, Unmatched) => reject
    }
  }
}

object Http2BlueprintInterceptor {

  case class HandlerWithEndpoint(interface: String, port: Int, handler: HttpRequest => Future[HttpResponse])
      extends (HttpRequest => Future[HttpResponse]) {

    override def apply(request: HttpRequest): Future[HttpResponse] = handler(request)
  }

  @RuntimeType
  def handleWithStreamIdHeader(
    @Argument(1) handler: HttpRequest => Future[HttpResponse],
    @SuperCall zuper: Callable[Flow[HttpRequest, HttpResponse, NotUsed]]
  ): Flow[HttpRequest, HttpResponse, NotUsed] = {

    handler match {
      case HandlerWithEndpoint(interface, port, _) =>
        ServerFlowWrapper(zuper.call(), interface, port)

      case _ =>
        zuper.call()
    }
  }
}
