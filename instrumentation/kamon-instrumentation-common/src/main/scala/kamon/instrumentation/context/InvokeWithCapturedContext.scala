/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
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
package context

import kamon.context.Storage
import kanela.agent.libs.net.bytebuddy.asm.Advice
import scala.annotation.static

/**
  * Advice that sets the Context from a HasContext instance as the current Context while the advised method is invoked.
  */
class InvokeWithCapturedContext private ()
object InvokeWithCapturedContext {

  @Advice.OnMethodEnter
  @static def enter(@Advice.This hasContext: HasContext): Storage.Scope =
    Kamon.storeContext(hasContext.context)

  @Advice.OnMethodExit(onThrowable = classOf[Throwable])
  @static def exit(@Advice.Enter scope: Storage.Scope): Unit =
    scope.close()
}
