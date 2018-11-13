/*
 * =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
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

package kamon.instrumentation.scala

import kamon.Kamon
import kamon.context.{Context, Storage}
import kamon.instrumentation.Mixin.HasContext
import kanela.agent.api.instrumentation.mixin.Initializer
import kanela.agent.scala.KanelaInstrumentation
import kanela.agent.libs.net.bytebuddy.asm.Advice

class FutureInstrumentation extends KanelaInstrumentation {

  /**
    * Instrument:
    *
    * scala.concurrent.impl.CallbackRunnable::run
    * scala.concurrent.impl.Future$PromiseCompletingRunnable::run
    *
    * Mix:
    *
    * scala.concurrent.impl.CallbackRunnable with kamon.scala.instrumentation.kanela.ContextAwareMixin
    * scala.concurrent.impl.Future$PromiseCompletingRunnable kamon.scala.instrumentation.kanela.ContextAwareMixin
    *
    */
  forTargetType("scala.concurrent.impl.CallbackRunnable" or "scala.concurrent.impl.Future$PromiseCompletingRunnable") { builder ⇒
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
    this._context = Kamon.currentContext()

  override def context: Context =
    _context
}


/**
  * Advisor for scala.concurrent.impl.CallbackRunnable::run
  * Advisor for scala.concurrent.impl.Future$PromiseCompletingRunnable::run
  */
class RunMethodAdvisor
object RunMethodAdvisor {

  @Advice.OnMethodEnter
  def enter(@Advice.This hasContext: HasContext): Storage.Scope =
    Kamon.storeContext(hasContext.context)

  @Advice.OnMethodExit(onThrowable = classOf[Throwable])
  def exit(@Advice.Enter scope: Storage.Scope): Unit =
    scope.close()
}

