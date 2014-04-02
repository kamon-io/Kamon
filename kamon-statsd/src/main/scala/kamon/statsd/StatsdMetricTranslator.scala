/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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
package kamon.statsd

import akka.actor.{Props, Actor, ActorRef}
import kamon.metrics.Subscriptions.TickMetricSnapshot

class StatsdMetricTranslator(receiver: ActorRef) extends Actor{

  def receive = {
    case TickMetricSnapshot(from, to, metrics) ⇒
      receiver ! ""
  }

}

object StatsdMetricTranslator {
  def props(receiver: ActorRef): Props = Props(new StatsdMetricTranslator(receiver))
}
