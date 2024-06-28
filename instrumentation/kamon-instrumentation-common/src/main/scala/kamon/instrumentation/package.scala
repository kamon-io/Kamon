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

import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.description.method.MethodDescription
import kanela.agent.libs.net.bytebuddy.matcher.ElementMatcher.Junction

package object instrumentation {

  trait AdviseWithCompanionObject {

    /**
      * Applies an advice to the provided method(s). The advice implementation is expected to be a companion object with
      * a method annotated with @Advice.OnMethodEnter and/or a method annotated with @Advice.OnMethodExit. Note that the
      * underlying implementation is expecting a class with static methods, which can only be provided by plain companion
      * objects; any nested companion object will not be appropriately picked up.
      */
    def advise[A](method: Junction[MethodDescription], advice: A)(implicit
      singletonEvidence: A <:< Singleton
    ): InstrumentationBuilder.Target
  }

  implicit def adviseWithCompanionObject(target: InstrumentationBuilder.Target): AdviseWithCompanionObject =
    new AdviseWithCompanionObject {

      override def advise[A](method: Junction[MethodDescription], advice: A)(implicit
        singletonEvidence: A <:< Singleton
      ): InstrumentationBuilder.Target = {
        // Companion object instances always have the '$' sign at the end of their class name, we must remove it to get
        // to the class that exposes the static methods.
        val className = advice.getClass.getName.dropRight(1)
        val adviseClass = Class.forName(className, true, advice.getClass.getClassLoader)

        target.advise(method, adviseClass)
      }
    }
}
