/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

import kamon.Kamon
import play.api.inject.{ApplicationLifecycle, Binding, Module}
import play.api.{Configuration, Environment, Logger}

import scala.concurrent.Future

class GuiceModule extends Module {
  def bindings(environment: Environment, configuration: Configuration): Seq[Binding[GuiceModule.KamonLoader]] = {
    Seq(bind[GuiceModule.KamonLoader].toSelf.eagerly())
  }
}

object GuiceModule {
  @Singleton
  class KamonLoader @Inject() (lifecycle: ApplicationLifecycle, environment: Environment, configuration: Configuration) {
    Logger(classOf[KamonLoader]).info("Reconfiguring Kamon with Play's Config")
    Kamon.reconfigure(configuration.underlying)
    Kamon.loadReportersFromConfig()

    lifecycle.addStopHook { () ⇒
      Future.successful(Kamon.stopAllReporters())
    }
  }
}
