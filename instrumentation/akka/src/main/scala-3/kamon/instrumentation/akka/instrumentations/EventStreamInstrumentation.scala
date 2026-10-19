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

package kamon.instrumentation.akka.instrumentations

import kanela.agent.api.instrumentation.InstrumentationBuilder

class EventStreamInstrumentation extends InstrumentationBuilder {

  /**
   * Counts dead letters and unhandled messages as they are published on the EventStream.
   */
  onType("akka.event.EventStream")
    .mixin(classOf[HasSystem.Mixin])
    .advise(isConstructor.and(takesArguments(2)), ConstructorAdvice)
  onType("akka.event.SubchannelClassification")
    .advise(method("publish").and(takesArguments(1)), PublishMethodAdvice)
}
