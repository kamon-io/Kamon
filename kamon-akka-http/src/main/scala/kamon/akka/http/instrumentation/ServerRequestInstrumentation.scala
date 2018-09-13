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
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.server.PathMatcher.{Matched, Unmatched}
import akka.http.scaladsl.server.directives.RouteDirectives.reject
import akka.http.scaladsl.server.directives.{BasicDirectives, PathDirectives}
import akka.http.scaladsl.server.{PathMatcher, RequestContext}
import akka.http.scaladsl.settings.ServerSettings
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.{Around, Aspect, DeclareMixin}
import kamon.Kamon

@Aspect
class ServerRequestInstrumentation extends BasicDirectives with PathDirectives  {

  @Around("execution(* akka.http.scaladsl.HttpExt.bindAndHandle(..)) && args(handler, interface, port, connectionContext, settings, log, materializer)")
  def onBindAndHandle(pjp: ProceedingJoinPoint, handler: Flow[HttpRequest, HttpResponse, Any], interface: String,
      port: Int, connectionContext: ConnectionContext, settings: ServerSettings, log: LoggingAdapter, materializer: Materializer): AnyRef = {

    val originalFLow = handler.asInstanceOf[Flow[HttpRequest, HttpResponse, NotUsed]]
    pjp.proceed(Array(ServerFlowWrapper(originalFLow, interface, port), interface, Int.box(port), connectionContext, settings, log, materializer))
  }

  @Around("execution(* akka.http.scaladsl.server.directives.PathDirectives.rawPathPrefix(..)) && args(matcher)")
  def aroundExtract[T](pjp: ProceedingJoinPoint, matcher: PathMatcher[T]): AnyRef = {
    implicit val LIsTuple = matcher.ev
    extract(ctx => {
      val fullPath = ctx.unmatchedPath.toString()
      val matching = matcher(ctx.unmatchedPath)
      matching match {
        case m: Matched[_] =>
          ctx.asInstanceOf[MatchingContext].prepend(PathMatchContext(fullPath, m))
        case _ => //TODO mark as rejected?
      }
      matching
    }).flatMap {
      case Matched(rest, values) ⇒ tprovide(values) & mapRequestContext(_ withUnmatchedPath rest)
      case Unmatched             ⇒ reject
    }
  }


  @Around("execution(* akka.http.scaladsl.server.RequestContextImpl.complete(..)) && this(ctx)")
  def aroundCtxComplete(pjp: ProceedingJoinPoint, ctx: RequestContext): AnyRef = {
    val allMatches = ctx.asInstanceOf[MatchingContext].matchingContext.reverse.map(singleMatch)
    val operationName = allMatches.mkString("")
    Kamon.currentSpan().setOperationName(operationName)
    pjp.proceed()
  }


  private def singleMatch(matching: PathMatchContext): String = {
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
        values.flatten.fold(consumedSegment)((full, value) => full.replaceFirst(s"(?i)$value", "{}"))
    }
  }


  @Around("execution(* akka.http.scaladsl.server.RequestContextImpl.copy(..)) && this(ctx)")
  def aroundCtxCopy(pjp: ProceedingJoinPoint, ctx: RequestContext): AnyRef = {
    val copy = pjp.proceed()
    copy.asInstanceOf[MatchingContext].set(ctx.asInstanceOf[MatchingContext].matchingContext)
    copy
  }


}

case class PathMatchContext(fullPath: String, matched: Matched[_])
trait MatchingContext {
  def matchingContext: Seq[PathMatchContext]
  def set(ctx: Seq[PathMatchContext])
  def prepend(matched: PathMatchContext)
}

object MatchingContext {
  def apply(): MatchingContext = new MatchingContext {
    var matchingContext: Seq[PathMatchContext] = Seq.empty
    override def prepend(matched: PathMatchContext): Unit = matchingContext = matched +: matchingContext
    override def set(ctx: Seq[PathMatchContext]): Unit = matchingContext = ctx
  }
}

@Aspect
class MatchingContextIntoRequestContext {

  @DeclareMixin("akka.http.scaladsl.server.RequestContextImpl")
  def matchedSegmentsToContext: MatchingContext = MatchingContext()

}