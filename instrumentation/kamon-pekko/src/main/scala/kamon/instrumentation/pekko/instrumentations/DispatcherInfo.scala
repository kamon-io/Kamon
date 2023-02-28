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

import org.apache.pekko.dispatch.DispatcherPrerequisites

object DispatcherInfo {

  trait HasDispatcherPrerequisites {
    def dispatcherPrerequisites: DispatcherPrerequisites
    def setDispatcherPrerequisites(dispatcherPrerequisites: DispatcherPrerequisites): Unit
  }

  object HasDispatcherPrerequisites {
    class Mixin extends HasDispatcherPrerequisites {
      @volatile private var _dispatcherPrerequisites: DispatcherPrerequisites = _
      override def dispatcherPrerequisites: DispatcherPrerequisites = _dispatcherPrerequisites
      override def setDispatcherPrerequisites(dispatcherPrerequisites: DispatcherPrerequisites): Unit =
        _dispatcherPrerequisites = dispatcherPrerequisites
    }
  }

  trait HasDispatcherName {
    def dispatcherName: String
    def setDispatcherName(dispatcherName: String): Unit
  }

  object HasDispatcherName {
    class Mixin extends HasDispatcherName {
      @volatile private var _dispatcherName: String = _
      override def dispatcherName: String = _dispatcherName
      override def setDispatcherName(dispatcherName: String): Unit = _dispatcherName = dispatcherName
    }
  }
}