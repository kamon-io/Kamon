/*
 *  ==========================================================================================
 *  Copyright © 2013-2022 The Kamon Project <https://kamon.io/>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 *  except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the
 *  License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied. See the License for the specific language governing permissions
 *  and limitations under the License.
 *  ==========================================================================================
 */

package kamon
package instrumentation
package futures
package twitter

import kamon.context.Context
import kamon.instrumentation.context.{CaptureCurrentContextOnExit, HasContext, InvokeWithCapturedContext}
import kanela.agent.api.instrumentation.InstrumentationBuilder

class TwitterFutureInstrumentation extends InstrumentationBuilder {

  onTypes("com.twitter.util.Promise$Transformer", "com.twitter.util.Promise$Monitored")
    .mixin(classOf[HasContext.Mixin])
    .advise(isConstructor, CaptureCurrentContextOnExit)
    .advise(method("apply"), InvokeWithCapturedContext)

  onType("com.twitter.util.Promise$Interruptible")
    .advise(isConstructor, classOf[InterruptiblePromiseConstructorAdvice])
}

class InterruptibleHandlerWithContext(context: Context, delegate: PartialFunction[Throwable, Unit])
    extends PartialFunction[Throwable, Unit] {

  override def isDefinedAt(x: Throwable): Boolean =
    delegate.isDefinedAt(x)

  override def apply(v1: Throwable): Unit =
    Kamon.runWithContext(context)(delegate.apply(v1))
}
