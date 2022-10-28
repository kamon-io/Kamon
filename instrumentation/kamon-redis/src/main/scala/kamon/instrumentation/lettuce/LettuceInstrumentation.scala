package kamon.instrumentation.lettuce

import io.lettuce.core.protocol.{AsyncCommand, RedisCommand}
import kamon.Kamon
import kamon.trace.Span
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice

import scala.annotation.static

class LettuceInstrumentation extends InstrumentationBuilder {
  onType("io.lettuce.core.AbstractRedisAsyncCommands")
    .advise(method("dispatch")
      .and(takesOneArgumentOf("io.lettuce.core.protocol.RedisCommand")), classOf[AsyncCommandAdvice])
}

class AsyncCommandAdvice
object AsyncCommandAdvice {
  @Advice.OnMethodEnter()
  @static def enter(@Advice.Argument(0) command: RedisCommand[_, _, _]): Span = {
    val spanName = s"redis.command.${command.getType}"
    Kamon.clientSpanBuilder(spanName, "redis.client.lettuce")
      .start();
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable],suppress = classOf[Throwable])
  @static def exit(@Advice.Enter span: Span,
           @Advice.Thrown t: Throwable,
           @Advice.Return asyncCommand: AsyncCommand[_, _, _]) = {
    if (t != null) {
      span.fail(t);
    }
    asyncCommand.handleAsync(new AsyncCommandCallback(span));
  }
}
