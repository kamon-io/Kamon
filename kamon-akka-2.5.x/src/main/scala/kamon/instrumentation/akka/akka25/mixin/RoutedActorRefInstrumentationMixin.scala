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

package kamon.instrumentation.akka.akka25.mixin

import akka.actor.Props
import akka.kamon.instrumentation.RoutedActorRefAccessor

/**
  * Mixin for akka.routing.RoutedActorCell
  */
class RoutedActorRefInstrumentationMixin extends RoutedActorRefAccessor {
  @volatile private var __routeeProps: Props = _
  @volatile private var __routerProps: Props = _

  override def routeeProps: Props = __routeeProps
  override def setRouteeProps(props: Props): Unit = __routeeProps = props

  override def routerProps: Props = __routerProps
  override def setRouterProps(props: Props): Unit = __routerProps = props
}

