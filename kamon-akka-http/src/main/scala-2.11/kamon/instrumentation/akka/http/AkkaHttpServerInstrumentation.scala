package kamon.instrumentation.akka.http

import java.util.concurrent.Callable

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes.Redirection
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.PathMatcher.{Matched, Unmatched}
import akka.http.scaladsl.server.directives.BasicDirectives
import akka.http.scaladsl.server.directives.RouteDirectives.reject
import akka.http.scaladsl.server._
import kamon.Kamon
import kamon.instrumentation.akka.http.HasMatchingContext.PathMatchingContext
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.api.instrumentation.mixin.Initializer
import kanela.agent.libs.net.bytebuddy.implementation.bind.annotation._


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
    * The rest of these sections are just about making sure that we can generate an appropriate operation name (i.e. free
    * of variables) and take a Sampling Decision in case none has been taken so far.
    */
  onType("akka.http.scaladsl.server.RequestContextImpl")
    .mixin(classOf[HasMatchingContext.Mixin])
    .intercept(method("copy"), RequestContextCopyInterceptor)

  onType("akka.http.scaladsl.server.directives.PathDirectives$class")
    .intercept(method("rawPathPrefix"), classOf[PathDirectivesRawPathPrefixInterceptor])

  onType("akka.http.scaladsl.server.directives.RouteDirectives$class")
    .intercept(method("complete"), classOf[ResolveOperationNameOnRouteInterceptor])
    .intercept(method("redirect"), classOf[ResolveOperationNameOnRouteInterceptor])
    .intercept(method("failWith"), classOf[ResolveOperationNameOnRouteInterceptor])
}


trait HasMatchingContext {
  def defaultOperationName: String
  def matchingContext: Seq[PathMatchingContext]
  def setMatchingContext(ctx: Seq[PathMatchingContext])
  def setDefaultOperationName(defaultOperationName: String)
  def prependMatchingContext(matched: PathMatchingContext)
}

object HasMatchingContext {

  case class PathMatchingContext (
    fullPath: String,
    matched: Matched[_]
  )

  class Mixin(var matchingContext: Seq[PathMatchingContext], var defaultOperationName: String) extends HasMatchingContext {

    override def setMatchingContext(matchingContext: Seq[PathMatchingContext]): Unit =
      this.matchingContext = matchingContext

    override def setDefaultOperationName(defaultOperationName: String): Unit =
      this.defaultOperationName = defaultOperationName

    override def prependMatchingContext(matched: PathMatchingContext): Unit =
      matchingContext = matched +: matchingContext

    @Initializer
    def initialize(): Unit =
      matchingContext = Seq.empty
  }
}

class ResolveOperationNameOnRouteInterceptor
object ResolveOperationNameOnRouteInterceptor {

  // We are replacing some of the basic directives here to ensure that we will resolve both the Sampling Decision and
  // the operation name before the request gets to the actual handling code (presumably inside of a "complete"
  // directive.

  def complete(@Argument(1) m: ⇒ ToResponseMarshallable): StandardRoute =
    StandardRoute(resolveOperationName(_).complete(m))

  def redirect(@Argument(1) uri: Uri, @Argument(2) redirectionType: Redirection): StandardRoute =
    StandardRoute(resolveOperationName(_).redirect(uri, redirectionType))

  def failWith(@Argument(1) error: Throwable): StandardRoute = {
    Kamon.currentSpan().fail(error)
    StandardRoute(resolveOperationName(_).fail(error))
  }

  private def resolveOperationName(requestContext: RequestContext): RequestContext = {
    val defaultOperationName = ServerFlowWrapper.defaultOperationName(requestContext.request.uri.authority.port)

    // We will only change the operation name if no change was applied to it. At this point, the only way in which it
    // might have changed is if the user changed it with the operationName directive or just accessing the Span and
    // changing it there, so we wouldn't want to overwrite that.
    //
    if(Kamon.currentSpan().operationName() == defaultOperationName) {
      val allMatches = requestContext.asInstanceOf[HasMatchingContext].matchingContext.reverse.map(singleMatch)
      val operationName = allMatches.mkString("")

      if(operationName.nonEmpty) {
        Kamon.currentSpan()
          .name(operationName)
          .takeSamplingDecision()
      }
    }

    requestContext
  }

  private def singleMatch(matching: PathMatchingContext): String = {
    val rest = matching.matched.pathRest.toString()
    val consumedCount = matching.fullPath.length - rest.length
    val consumedSegment = matching.fullPath.substring(0, consumedCount)

    matching.matched.extractions match {
      case () => //string segment matched
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
          val r = s"(?i)(^|/)" + value + "($|/)"
          full.replaceFirst(r, "$1{}$2")
        }
    }
  }
}

object RequestContextCopyInterceptor {

  @RuntimeType
  def copy(@This context: RequestContext, @SuperCall copyCall: Callable[RequestContext]): RequestContext = {
    val copiedRequestContext = copyCall.call()
    copiedRequestContext.asInstanceOf[HasMatchingContext].setMatchingContext(context.asInstanceOf[HasMatchingContext].matchingContext)
    copiedRequestContext
  }
}

class PathDirectivesRawPathPrefixInterceptor
object PathDirectivesRawPathPrefixInterceptor {
  import BasicDirectives._

  @RuntimeType
  def rawPathPrefix[T](@Argument(1) matcher: PathMatcher[T]): Directive[T] = {
    implicit val LIsTuple = matcher.ev

    extract(ctx => {
      val fullPath = ctx.unmatchedPath.toString()
      val matching = matcher(ctx.unmatchedPath)
      matching match {
        case m: Matched[_] =>
          ctx.asInstanceOf[HasMatchingContext].prependMatchingContext(PathMatchingContext(fullPath, m))
        case _ =>
      }
      matching
    }).flatMap {
      case Matched(rest, values) ⇒ tprovide(values) & mapRequestContext(_ withUnmatchedPath rest)
      case Unmatched             ⇒ reject
    }
  }
}