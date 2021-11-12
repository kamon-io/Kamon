/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon.instrumentation.jedis

import kamon.Kamon
import kamon.trace.Span
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice

class JedisInstrumentation extends InstrumentationBuilder {
  onType("redis.clients.jedis.Protocol")
    .advise(method("sendCommand"), classOf[SendCommandAdvice])
}

class SendCommandAdvice

object SendCommandAdvice {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def enter(@Advice.Argument(1) command: Any): Span = {
    val spanName = s"redis.command.$command"
    Kamon.clientSpanBuilder(spanName, "redis.client.jedis")
      .start()
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  def exit(@Advice.Enter span: Span,
           @Advice.Thrown t: Throwable): Unit = {
    if (t != null) {
      span.fail(t)
    }
    span.finish()
  }
}
