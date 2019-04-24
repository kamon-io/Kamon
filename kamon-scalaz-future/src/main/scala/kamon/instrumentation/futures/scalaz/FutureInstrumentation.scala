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

package kamon.instrumentation.futures.scalaz

import kamon.instrumentation.context.{CaptureCurrentContext, HasContext, InvokeWithCapturedContext}
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.description.`type`.TypeDescription
import kanela.agent.libs.net.bytebuddy.matcher.ElementMatcher.Junction
import kanela.agent.libs.net.bytebuddy.matcher.ElementMatchers._

class FutureInstrumentation extends InstrumentationBuilder {

  val scalazCallable: Junction[TypeDescription] = hasSuperType(anyTypes("java.util.concurrent.Callable"))
    .and(nameStartsWith("scalaz.concurrent"))

  onTypesMatching(scalazCallable)
    .mixin(classOf[HasContext.Mixin])
    .advise(isConstructor, CaptureCurrentContext)
    .advise(method("call"), InvokeWithCapturedContext)

}