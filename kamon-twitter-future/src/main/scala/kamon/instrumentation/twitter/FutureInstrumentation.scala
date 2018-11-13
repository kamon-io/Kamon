/*
 * =========================================================================================
 * Copyright © 2016 the kamon project <http://kamon.io/>
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

package kamon.instrumentation.twitter

import kamon.Kamon
import kamon.context.{Context, Storage}
import kamon.instrumentation.Mixin.HasContext
import kanela.agent.api.instrumentation.mixin.Initializer
import kanela.agent.scala.KanelaInstrumentation
import kanela.agent.libs.net.bytebuddy.asm.Advice
import kanela.agent.libs.net.bytebuddy.description.`type`.TypeDescription
import kanela.agent.libs.net.bytebuddy.matcher.ElementMatcher.Junction
import kanela.agent.libs.net.bytebuddy.matcher.ElementMatchers._

class FutureInstrumentation extends KanelaInstrumentation {

  val matcher: Junction[TypeDescription] = hasSuperType(anyTypes("java.lang.Runnable"))
    .and(nameStartsWith("com.twitter.util")
    .and(not(nameStartsWith("com.twitter.util.ConstFuture"))))

  forRawMatching(matcher) { builder =>
    builder
      .withMixin(classOf[HasContextMixin])
      .withAdvisorFor(method("run"), classOf[RunMethodAdvisor])
      .build()
  }
}


class HasContextMixin extends HasContext {
  private var _context: Context = _

  @Initializer
  def initialize(): Unit =
    _context = Kamon.currentContext()

  override def context: Context =
    _context
}

class RunMethodAdvisor
object RunMethodAdvisor {

  @Advice.OnMethodEnter
  def enter(@Advice.This hasContext: HasContext): Storage.Scope =
    Kamon.storeContext(hasContext.context)

  @Advice.OnMethodExit(onThrowable = classOf[Throwable])
  def exit(@Advice.Enter scope: Storage.Scope): Unit =
    scope.close()
}