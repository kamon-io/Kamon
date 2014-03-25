/* ===================================================
 * Copyright © 2013 2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */

import akka.actor
import akka.actor._

import kamon.metrics.Subscriptions.TickMetricSnapshot
import kamon.metrics.{Metrics, TraceMetrics}
import kamon.Kamon
import kamon.trace.TraceRecorder

case object BeginWithoutTraceContext
case object BeginWithTraceContext
case object Finish

object AkkaWithKamonExample extends App {
  implicit val system = ActorSystem("simple-context-propagation")

  val simpleMetricExtension = SimpleExtension(system)

  val mainActor = system.actorOf(Props[JobSender], "mainActor")

  for (_ <- 1 to 5) mainActor ! BeginWithTraceContext
}

class JobSender extends Actor with ActorLogging {
  val upperCaser = context.actorOf(Props[UpperCaser], "upper-caser")
  var counter = 1

  def receive = {
    case BeginWithTraceContext => {
      //TraceRecorder requires an implicit ActorSystem
      implicit val system = context.system
        TraceRecorder.withNewTraceContext("uppercaser") {
          upperCaser ! "Hello World with TraceContext"
      }
    }
    case length: Int => {
      log.info("Length [{}]", length)
      self ! Finish
    }

    case Finish => {
      log.info("Finishing request {}", counter += 1)
      TraceRecorder.finish()

    if (counter == 5) {
      Thread.sleep(10000)
      log.info("Shutting down the system")
      context.system.shutdown()
      }
    }
  }
}

class UpperCaser extends Actor with ActorLogging {
  val lengthCalculator = context.actorOf(Props[LengthCalculator], "length-calculator")

  def receive = {
    case anyString: String => {
      log.info("Upper casing [{}]", anyString)
      lengthCalculator.forward(anyString.toUpperCase)
    }
  }
}

class LengthCalculator extends Actor with ActorLogging  {
  def receive = {
    case anyString: String =>
      log.info("Calculating the length of: [{}]", anyString)
      sender ! anyString.length
  }
}

class SimpleExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  val metricsListener = system.actorOf(Props[SimpleMetricsListener])

  Kamon(Metrics)(system).subscribe(TraceMetrics, "*", metricsListener, permanently = true)

}

class SimpleMetricsListener extends Actor with ActorLogging {
  log.info("Starting the Kamon(SimpleMetricsListener) extension")

  override def receive: Actor.Receive = {
    case TickMetricSnapshot(tickFrom, tickTo, metrics) => log.info("Tick From: {} nano To: {} nano => {}", tickFrom, tickTo, metrics)
  }
}

object SimpleExtension extends ExtensionId[SimpleExtension] with ExtensionIdProvider {
  def lookup(): ExtensionId[_ <: actor.Extension] = SimpleExtension
  def createExtension(system: ExtendedActorSystem): SimpleExtension = new SimpleExtension(system)

}
