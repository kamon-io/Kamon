package kamon.instrumentation.jedis

import kamon.Kamon
import kamon.context.Storage.Scope
import kamon.trace.Span
import kamon.trace.Trace.SamplingDecision
import redis.clients.jedis.commands.ProtocolCommand
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice

class JedisInstrumentation extends InstrumentationBuilder {
  onType("redis.clients.jedis.Protocol")
    .advise(method("sendCommand").and(withArgument(1, classOf[ProtocolCommand])), classOf[SendCommandAdvice])

  onType("redis.clients.jedis.Jedis")
    // we're gonna have a looot of these
    .advise(method("set"), classOf[JedisCommandAdvice])
    .advise(method("get"), classOf[JedisCommandAdvice])
  // 40 methods instrumented
  // dude, make sure shit isnt double instrumented
}

class JedisCommandAdvice
object JedisCommandAdvice {
  @Advice.OnMethodEnter()
  def enter(@Advice.Origin("#m") methodName: String) = {
    // operation name default should be in conf
    println(s"method name is ${methodName}")
    println(s"method name is ${methodName}")
    println(s"method name is ${methodName}")
    println(s"method name is ${methodName}")
    println(s"method name is ${methodName}")
    val span = Kamon.clientSpanBuilder("redis.command.unknown", "redis.client.jedis")
      // remove later
      .samplingDecision(SamplingDecision.Unknown)
      .start()

    (Kamon.storeContext(Kamon.currentContext().withEntry(Span.Key, span)), span)
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable])
  def exit(@Advice.Enter enter: (Scope, Span),
          // is this ok
           @Advice.Thrown t: Throwable) = {
    val (scope, span) = enter
    // when should I fail the span?
    // is this throwable thing ok?
    // how do I even test that it's good?
    if (t != null) {
      span.fail(t)
    }
    span.finish()
    scope.close()
  }
}

class SendCommandAdvice
object SendCommandAdvice {
  @Advice.OnMethodEnter()
  def enter(@Advice.Argument(1) command: ProtocolCommand) = {
    val span = Kamon.currentSpan()
    if (span.operationName() == "redis.command.unknown") {
      span.name(s"redis.command.${command}")
        // remove sampling
        .takeSamplingDecision()
    }
  }
}
