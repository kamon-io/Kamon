/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

package kamon.jmx

import javax.management.{ JMException, JMRuntimeException }

import akka.actor._
import akka.event.Logging
import kamon.Kamon
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.util.ConfigTools.Syntax
import scala.collection.JavaConverters._

object JMXReporter extends ExtensionId[JMXExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = JMXReporter

  override def createExtension(system: ExtendedActorSystem): JMXExtension = new JMXExtension(system)
}

class JMXExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  val log = Logging(system, getClass)
  log.info("Starting the Kamon(JMX) extension")

  val subscriber = system.actorOf(Props[JMXReporterSupervisor], "kamon-jmx-reporter")

  val jmxConfig = system.settings.config.getConfig("kamon.jmx")
  val subscriptions = jmxConfig.getConfig("subscriptions")

  subscriptions.firstLevelKeys foreach { subscriptionCategory ⇒
    subscriptions.getStringList(subscriptionCategory).asScala.foreach { pattern ⇒
      Kamon.metrics.subscribe(subscriptionCategory, pattern, subscriber, permanently = true)
    }
  }
}

private trait ActorJMXSupervisor extends Actor with ActorLogging {

  import akka.actor.OneForOneStrategy
  import akka.actor.SupervisorStrategy._

  import scala.concurrent.duration._

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case e: JMRuntimeException ⇒
        log.error(e, "Supervisor strategy STOPPING actor from errors during JMX invocation")
        Stop
      case e: JMException ⇒
        log.error(e, "Supervisor strategy STOPPING actor from incorrect invocation of JMX registration")
        Stop
      case t ⇒
        // Use the default supervisor strategy otherwise.
        super.supervisorStrategy.decider.applyOrElse(t, (_: Any) ⇒ Escalate)
    }
}

private class JMXReporterSupervisor extends Actor with ActorLogging with ActorJMXSupervisor {
  private val jmxActor = context.actorOf(JMXReporterActor.props, "kamon-jmx-actor")

  def receive = {
    case tick: TickMetricSnapshot ⇒ jmxActor ! tick
  }
}