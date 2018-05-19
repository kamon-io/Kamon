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

package kamon.akka.instrumentation.kanela

import kamon.akka.instrumentation.kanela.advisor.{ConstructorAdvisor, PublishMethodAdvisor}
import kamon.akka.instrumentation.kanela.mixin.HasSystemMixin
import kanela.agent.scala.KanelaInstrumentation


class DeadLettersInstrumentation extends KanelaInstrumentation {

  /**
    * Mix:
    *
    * akka.event.EventStream with HasSystem
    *
    */
  forSubtypeOf("akka.event.EventStream") { builder ⇒
    builder
      .withMixin(classOf[HasSystemMixin])
      .withAdvisorFor(Constructor, classOf[ConstructorAdvisor])
      .withAdvisorFor(method("publish").and(takesArguments(1)), classOf[PublishMethodAdvisor])
      .build()
  }
}
