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

package kamon.instrumentation.executor

import kamon.instrumentation.context.{CaptureCurrentContextOnExit, HasContext, InvokeWithCapturedContext}
import kanela.agent.api.instrumentation.InstrumentationBuilder

/**
  * Captures the current Context upon creation of all Runnable/Callable implementations and sets that Context as current
  * while their run/call methods are executed. See the module's exclude configuration for more info on what packages and
  * implementations will not be targeted by this instrumentation (e.g. it does not target any java.* class by default).
  */
class ExecutorTaskInstrumentation extends InstrumentationBuilder {

  onSubTypesOf("java.lang.Runnable", "java.util.concurrent.Callable")
    .mixin(classOf[HasContext.Mixin])
    .advise(isConstructor, CaptureCurrentContextOnExit)
    .advise(anyMethods("run", "call"), InvokeWithCapturedContext)
}
