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

package kamon.instrumentation.pekko.instrumentations

import kamon.instrumentation.context.{HasContext, HasTimestamp}
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice

import scala.annotation.static

class EnvelopeInstrumentation extends InstrumentationBuilder {

  /**
    * Ensures that the Pekko Envelope is able to carry around a Context and a Timestamp.
    */
  onType("org.apache.pekko.dispatch.Envelope")
    .mixin(classOf[HasContext.Mixin])
    .mixin(classOf[HasTimestamp.Mixin])
    .advise(method("copy"), classOf[EnvelopeCopyAdvice])
}

class EnvelopeCopyAdvice
object EnvelopeCopyAdvice {

  @Advice.OnMethodExit
  @static def exit(@Advice.Return newEnvelope: Any, @Advice.This envelope: Any): Unit = {
    newEnvelope.asInstanceOf[HasContext].setContext(envelope.asInstanceOf[HasContext].context)
    newEnvelope.asInstanceOf[HasTimestamp].setTimestamp(envelope.asInstanceOf[HasTimestamp].timestamp)
  }
}
