/* =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License") you may not use this file
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

package kamon.play.di

import javax.inject._
import kamon.metric.MetricsModule
import kamon.trace.TracerModule
import play.api.inject.{ ApplicationLifecycle, Module }
import play.api.{ Logger, Configuration, Environment }
import scala.concurrent.Future

trait Kamon {
  def start(): Unit
  def shutdown(): Unit
  def metrics(): MetricsModule
  def tracer(): TracerModule
}

class KamonModule extends Module {
  def bindings(environment: Environment, configuration: Configuration) = {
    Seq(bind[Kamon].to[KamonAPI].eagerly())
  }
}

@Singleton
class KamonAPI @Inject() (lifecycle: ApplicationLifecycle, environment: Environment) extends Kamon {
  private val log = Logger(classOf[KamonAPI])

  log.info("Registering the Kamon Play Module.")

  start() //force to start kamon eagerly on application startup

  def start(): Unit = kamon.Kamon.start()
  def shutdown(): Unit = kamon.Kamon.shutdown()
  def metrics(): MetricsModule = kamon.Kamon.metrics
  def tracer(): TracerModule = kamon.Kamon.tracer

  lifecycle.addStopHook { () ⇒
    Future.successful(shutdown())
  }
}
