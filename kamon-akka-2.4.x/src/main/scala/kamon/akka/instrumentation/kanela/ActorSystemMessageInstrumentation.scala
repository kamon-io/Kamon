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

import kamon.akka.instrumentation.kanela.interceptor.PointMethodInterceptor
import kanela.agent.scala.KanelaInstrumentation

class ActorSystemMessageInstrumentation extends KanelaInstrumentation {

  /**
    * Mix:
    *
    * akka.dispatch.sysmsg.SystemMessage with kamon.trace.TraceContextAware
    *
    */
  forSubtypeOf("akka.dispatch.sysmsg.SystemMessage") { builder ⇒
    builder
      .withMixin(classOf[HasTransientContextMixin])
      .build()
  }

  /**
    * Instrument:
    *
    * akka.actor.RepointableActorRef::point
    *
    * Mix:
    *
    * akka.actor.RepointableActorRef with kamon.trace.TraceContextAware
    *
    */
  forTargetType("akka.actor.RepointableActorRef") { builder ⇒
    builder
      .withMixin(classOf[HasTransientContextMixin])
      .withInterceptorFor(method("point"), PointMethodInterceptor)
      .build()
  }
}