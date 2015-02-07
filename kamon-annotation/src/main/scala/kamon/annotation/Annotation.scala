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

package kamon.annotation

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import akka.event.Logging
import kamon.Kamon

object Annotation extends ExtensionId[AnnotationExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = Annotation
  override def createExtension(system: ExtendedActorSystem): AnnotationExtension = new AnnotationExtension(system)
}

class AnnotationExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  val log = Logging(system, classOf[AnnotationExtension])
  log.info(s"Starting the Kamon(Annotation) extension")

  val config = system.settings.config.getConfig("kamon.annotation")
  val arraySize = config.getInt("instruments-array-size")
}

