package kamon.instrumentation.jedis

import kamon.Kamon
import kamon.trace.Span
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice
import redis.clients.jedis.params.XAddParams

class JedisInstrumentation extends InstrumentationBuilder {
  val jedisTarget = onType("redis.clients.jedis.Jedis")
  JedisClientMethods.commands.foreach(m => jedisTarget.advise(method(m), classOf[JedisCommandAdvice]))

  // matchers that make sure that these methods create only one span
  jedisTarget.advise(method("bitpos").and(takesArguments(3)), classOf[JedisCommandAdvice])
    .advise(method("eval").and(takesArguments(3))
      .and(withArgument(1, classOf[List[String]])), classOf[JedisCommandAdvice])
    .advise(method("eval").and(takesArguments(1)), classOf[JedisCommandAdvice])
    .advise(method("evalsha").and(takesArguments(3))
      .and(withArgument(1, classOf[List[String]])), classOf[JedisCommandAdvice])
    .advise(method("evalsha").and(takesArguments(1)), classOf[JedisCommandAdvice])
    .advise(method("scan").and(takesArguments(2)), classOf[JedisCommandAdvice])
    .advise(method("hscan").and(takesArguments(3)), classOf[JedisCommandAdvice])
    .advise(method("shscan").and(takesArguments(3)), classOf[JedisCommandAdvice])
    .advise(method("zscan").and(takesArguments(3)), classOf[JedisCommandAdvice])
    .advise(method("xadd").and(takesArguments(5)), classOf[JedisCommandAdvice])
    .advise(method("xadd").and(takesArguments(5))
      .and(withArgument(2, classOf[XAddParams])), classOf[JedisCommandAdvice])
}

class JedisCommandAdvice

object JedisCommandAdvice {
  @Advice.OnMethodEnter()
  def enter(@Advice.Origin("#m") methodName: String) = {
    val spanName = s"redis.command.${methodName.toUpperCase}"
    val span = Kamon.clientSpanBuilder(spanName, "redis.client.jedis")
      .start()

    span
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
