package kamon.instrumentation.rediscala

import kamon.Kamon
import kamon.trace.Span
import kamon.util.CallingThreadExecutionContext
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice

import scala.concurrent.Future
import scala.util.{Failure, Success}

class RediscalaInstrumentation extends InstrumentationBuilder {
  onTypes("redis.Request", "redis.ActorRequest", "redis.BufferedRequest",
    "redis.commands.BLists", "redis.RoundRobinPoolRequest")
    .advise(method("send").and(takesArguments(1)), classOf[RequestInstrumentation])
}

class RequestInstrumentation

object RequestInstrumentation {
  @Advice.OnMethodEnter()
  def enter(@Advice.Argument(0) command: Any): Span = {
    val spanName = s"redis.command.${command.getClass.getSimpleName}"

    Kamon.clientSpanBuilder(spanName, "redis.client.rediscala")
      .start()
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  def exit(@Advice.Enter span: Span,
           @Advice.Thrown t: Throwable,
           @Advice.Return future: Future[_]) = {
    if (t != null) {
      span.fail(t);
    }

    future.onComplete {
      case Success(_value) =>
        span.finish()

      case Failure(exception) =>
        span.fail(exception)
        span.finish()

    }(CallingThreadExecutionContext)
  }
}

class RoundRobinRequestInstrumentation

object RoundRobinRequestInstrumentation {
  @Advice.OnMethodEnter()
  def enter(@Advice.Argument(1) command: Any): Span = {
    val spanName = s"redis.command.${command.getClass.getSimpleName}"
    Kamon.clientSpanBuilder(spanName, "redis.client.rediscala")
      .start()
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  def exit(@Advice.Enter span: Span,
           @Advice.Thrown t: Throwable,
           @Advice.Return future: Future[_]) = {
    if (t != null) {
      span.fail(t);
    }

    future.onComplete {
      case Success(_value) =>
        span.finish()

      case Failure(exception) =>
        span.fail(exception)
        span.finish()

    }(CallingThreadExecutionContext)
  }
}
