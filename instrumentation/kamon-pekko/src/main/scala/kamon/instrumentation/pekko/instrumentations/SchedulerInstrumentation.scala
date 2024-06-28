/* =========================================================================================
 * Copyright © 2013-2022 the kamon project <http://kamon.io/>
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

import kanela.agent.api.instrumentation.InstrumentationBuilder

class SchedulerInstrumentation extends InstrumentationBuilder {

  /**
    * Captures the current context when calling `scheduler.scheduleOnce` and restores it when the submitted runnable
    * runs. This ensures that certain Akka patterns like retry and after work as expected.
    */
  onSubTypesOf("org.apache.pekko.actor.Scheduler")
    .advise(method("scheduleOnce").and(withArgument(1, classOf[Runnable])), classOf[SchedulerRunnableAdvice])
}
