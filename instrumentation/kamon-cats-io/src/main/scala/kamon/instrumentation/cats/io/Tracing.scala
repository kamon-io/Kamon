package kamon.instrumentation.cats.io

import cats.effect.{ExitCase, Sync}
import cats.implicits._
import kamon.Kamon
import kamon.tag.TagSet
import kamon.trace.Span

object Tracing {

  /**
    * Wraps the effect `fa` in a new span with the provided name and tags. The created span is marked as finished after
    * the effect is completed or cancelled.
    *
    * @param name                 the span name
    * @param tags                 the collection of tags to apply to the span
    * @param takeSamplingDecision if true, it ensures that a Sampling Decision is taken in case none has been taken so far
    * @param fa                   the effect to execute
    * @tparam F the effect type
    * @tparam A the value produced in the effect F
    * @return the same effect wrapped within a named span
    */
  def operationName[F[_] : Sync, A](name: String, tags: Map[String, Any] = Map.empty, takeSamplingDecision: Boolean = true)(fa: F[A]): F[A] = {
    val F = implicitly[Sync[F]]
    buildSpan(name, tags).flatMap { span =>
      F.guaranteeCase(fa) {
        case ExitCase.Completed => F.delay(finishSpan(span, takeSamplingDecision))
        case ExitCase.Error(err) => F.delay(failSpan(span, err, takeSamplingDecision))
        case ExitCase.Canceled => F.delay(finishSpan(span.tag("cancel", value = true), takeSamplingDecision))
      }
    }
  }

  private def buildSpan[F[_]](name: String, tags: Map[String, Any])(implicit F: Sync[F]): F[Span] =
    F.delay(
      Kamon
        .serverSpanBuilder(name, "cats-effect")
        .asChildOf(Kamon.currentSpan())
        .tagMetrics(TagSet.from(tags))
        .start()
    )

  private def finishSpan(span: Span, takeSamplingDecision: Boolean): Span = {
    if (takeSamplingDecision) span.takeSamplingDecision()
    span.finish()
    span
  }

  private def failSpan(span: Span, err: Throwable, takeSamplingDecision: Boolean): Span = {
    if (err.getMessage == null) span.fail(err)
    else span.fail(err.getMessage, err)

    finishSpan(span, takeSamplingDecision)
  }

  object Implicits {

    final class KamonOps[F[_] : Sync, A](fa: F[A]) {
      /**
        * Wraps the effect in a new span with the provided name and tags. The created span is marked as finished after
        * the effect is completed or cancelled.
        *
        * @param name                 the span name
        * @param tags                 the collection of tags to apply to the span
        * @param takeSamplingDecision if true, it ensures that a Sampling Decision is taken in case none has been taken so far
        * @return the same effect wrapped within a named span
        */
      def named(name: String, tags: Map[String, Any] = Map.empty, takeSamplingDecision: Boolean = true): F[A] =
        operationName(name, tags, takeSamplingDecision)(fa)
    }

    implicit final def kamonTracingSyntax[F[_] : Sync, A](fa: F[A]): KamonOps[F, A] = new KamonOps(fa)
  }

}