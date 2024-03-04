package kamon.instrumentation.caffeine

import kamon.Kamon
import kamon.trace.Span
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice

import scala.annotation.static

class CaffeineCacheInstrumentation extends InstrumentationBuilder {
  onType("com.github.benmanes.caffeine.cache.LocalCache")
    .advise(method("computeIfAbsent"), classOf[SyncCacheAdvice])
    .advise(method("getIfPresent"), classOf[GetIfPresentAdvice])

  onType("com.github.benmanes.caffeine.cache.LocalManualCache")
    .advise(method("getAll"), classOf[SyncCacheAdvice])
    .advise(method("put"), classOf[SyncCacheAdvice])
    .advise(method("getIfPresent"), classOf[GetIfPresentAdvice])
    .advise(method("putAll"), classOf[SyncCacheAdvice])
    .advise(method("getAllPresent"), classOf[SyncCacheAdvice])
}

class SyncCacheAdvice
object SyncCacheAdvice {
  @Advice.OnMethodEnter()
  @static def enter(@Advice.Origin("#m") methodName: String) = {
    Kamon.clientSpanBuilder(s"caffeine.$methodName", "caffeine").start()
  }

  @Advice.OnMethodExit(suppress = classOf[Throwable])
  @static def exit(@Advice.Enter span: Span): Unit = {
    span.finish()
  }
}

class GetIfPresentAdvice
object GetIfPresentAdvice {
  @Advice.OnMethodEnter()
  @static def enter(@Advice.Origin("#m") methodName: String) = {
    Kamon.clientSpanBuilder(s"caffeine.$methodName", "caffeine").start()
  }

  @Advice.OnMethodExit(suppress = classOf[Throwable])
  @static def exit(@Advice.Enter span: Span, @Advice.Return ret: Any, @Advice.Argument(0) key: Any): Unit = {
    if (ret == null) {
      span.tag("cache.miss", s"No value for key $key")
    }
    span.finish()
  }
}
