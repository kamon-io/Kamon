package kamon.instrumentation.jedis

import kamon.Kamon
import kamon.trace.Span
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice
import redis.clients.jedis.commands.ProtocolCommand

class JedisInstrumentation extends InstrumentationBuilder {
  onType("redis.clients.jedis.Protocol")
    .advise(method("sendCommand"), classOf[SendCommandAdvice])
}

class SendCommandAdvice
object SendCommandAdvice {
  @Advice.OnMethodEnter()
  def enter(@Advice.Argument(1) command: Any) = {
    command match {
      case command: ProtocolCommand =>
        val spanName = s"redis.command.${command}"
        Kamon.clientSpanBuilder(spanName, "redis.client.jedis")
          .start()
      case _ => Span.Empty
    }
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable])
  def exit(@Advice.Enter span: Span,
           @Advice.Thrown t: Throwable) = {
    if (t != null) {
      span.fail(t)
    }
    span.finish()
  }
}
