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
package pekko
package connectors
package kafka

import kamon.Kamon
import kamon.context.Storage
import kamon.context.Storage.Scope
import kamon.instrumentation.context.HasContext
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice

class ProducerMessageInstrumentation extends InstrumentationBuilder {

  /**
    * Captures the current context the a Message or MultiMessage is created and restores it while
    * the ProducerLogic is running, so the proper context gets propagated to the Kafka Producer.
    */
  onTypes("org.apache.pekko.kafka.ProducerMessage$Message", "org.apache.pekko.kafka.ProducerMessage$MultiMessage")
    .mixin(classOf[HasContext.MixinWithInitializer])

  onTypes(
    "org.apache.pekko.kafka.internal.DefaultProducerStageLogic",
    "org.apache.pekko.kafka.internal.CommittingProducerSinkStageLogic"
  )
    .advise(method("produce"), ProduceWithEnvelopeContext)
}

object ProduceWithEnvelopeContext {

  @Advice.OnMethodEnter
  def enter(@Advice.Argument(0) envelope: Any): Storage.Scope = {
    envelope match {
      case hasContext: HasContext => Kamon.storeContext(hasContext.context)
      case _                      => Scope.Empty
    }
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable])
  def exit(@Advice.Enter scope: Storage.Scope): Unit =
    scope.close()
}
