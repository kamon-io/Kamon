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

package kamon
package instrumentation
package jedis

import kamon.context.Storage.Scope
import kamon.tag.Lookups.plain
import kamon.trace.Span
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice
import kanela.agent.libs.net.bytebuddy.description.method.MethodDescription
import kanela.agent.libs.net.bytebuddy.matcher.ElementMatchers.{isMethod, isPublic, isStatic, namedOneOf, not}

import scala.annotation.static

class JedisInstrumentation extends InstrumentationBuilder {

  /**
    * Most methods in `Jedis` and `BinaryJedis` end up sending calls to the Redis server, so we are
    * targeting all methods in those classes, except for the ones excluded below.
    */
  onTypes("redis.clients.jedis.Jedis", "redis.clients.jedis.BinaryJedis")
    .advise(
      isMethod[MethodDescription]()
        .and(isPublic[MethodDescription])
        .and(not(isStatic[MethodDescription]))
        .and(not(namedOneOf[MethodDescription](
          "setDataSource",
          "getDB",
          "isConnected",
          "connect",
          "disconnect",
          "resetState",
          "getClient",
          "getConnection",
          "isConnected",
          "isBroken",
          "close",
          "toString",
          "hashCode"
        ))),
      classOf[ClientOperationsAdvice])


  /**
    * For Jedis 3.x. This advice ensures we get the right command name in the Span.
    */
  onType("redis.clients.jedis.Protocol")
    .advise(
      method("sendCommand")
        .and(isPublic[MethodDescription])
        .and(isStatic[MethodDescription])
        .and(takesArguments(3)),
      classOf[SendCommandAdvice])

  /**
    * For Jedis 4.x. This advice ensures we get the right command name in the Span. Notice
    * how the method is the same, but there is a different number of arguments.
    */
  onType("redis.clients.jedis.Protocol")
    .when(classIsPresent("redis.clients.jedis.CommandArguments"))
    .advise(
      method("sendCommand")
        .and(isPublic[MethodDescription])
        .and(isStatic[MethodDescription])
        .and(takesArguments(2)),
      classOf[SendCommandAdviceForJedis4])


}

class ClientOperationsAdvice
object ClientOperationsAdvice {
  private val currentRedisOperationKey = "redis.current"

  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  @static def enter(@Advice.Origin("#m") methodName: String): Scope = {
    val currentContext = Kamon.currentContext()

    if(currentContext.getTag(plain(currentRedisOperationKey)) == null) {

      // The actual span name is going to be set in the SendCommand advice
      val clientSpan = Kamon
        .clientSpanBuilder("jedis", "redis.client.jedis")
        .tagMetrics("db.system", "redis")
        .start()

      Kamon.storeContext(currentContext
        .withEntry(Span.Key, clientSpan)
        .withTag(currentRedisOperationKey, methodName)
      )
    } else Scope.Empty
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  @static def exit(@Advice.Enter scope: Scope, @Advice.Thrown t: Throwable): Unit = {
    if(scope != Scope.Empty) {
      val span = scope.context.get(Span.Key)
      if (t != null) {
        span.fail(t)
      }

      span.finish()
      scope.close()
    }
  }
}

class SendCommandAdvice
object SendCommandAdvice {

  @Advice.OnMethodEnter
  @static def sendCommand(@Advice.Argument(1) command: Any): Unit = {
    // The command object should actually be an Enum and its toString() produces
    // the actual command name sent to Redis
    Kamon.currentSpan()
      .name(command.toString())
      .tag("db.operation", command.toString())
  }
}

class SendCommandAdviceForJedis4
object SendCommandAdviceForJedis4 {

  @Advice.OnMethodEnter
  @static def sendCommand(@Advice.Argument(1) commandArguments: Any): Unit = {
    val firstArgument = commandArguments.asInstanceOf[java.lang.Iterable[Any]].iterator().next()

    // The command object should actually be an Enum and its toString() produces
    // the actual command name sent to Redis
    Kamon.currentSpan()
      .name(firstArgument.toString())
      .tag("db.operation", firstArgument.toString())
  }
}

