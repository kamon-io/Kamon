/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
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
package status

import kamon.status.Status.Instrumentation.TypeError
import org.slf4j.LoggerFactory
import java.lang.{Boolean => JBoolean}
import java.util.{List => JavaList, Map => JavaMap}
import scala.collection.JavaConverters.{asScalaBufferConverter, mapAsScalaMapConverter}

/**
  * This object works as a bridge between Kamon and Kanela to gather information about instrumentation modules. When
  * instrumentation is enabled, it should be possible to access the instrumentation registry from the System
  * ClassLoader and determine what modules have been detected and activated.
  *
  * Since this data is coming from a different ClassLoader and possible not present when Kamon is started, we are
  * sharing all instrumentation information between Kanela and Kamon using JDK-only types.
  */
object InstrumentationStatus {

  private val _logger = LoggerFactory.getLogger("kamon.status.Status.Instrumentation")
  private val _kanelaLoadedPropertyName = "kanela.loaded"
  private val _registryClassName = "kanela.agent.api.instrumentation.listener.InstrumentationRegistryListener"


  /**
    * Tries to fetch the current instrumentation information from Kanela and assemble a status instance. Since the
    * status information will be present on the System ClassLoader only after the Kanela agent has been activated (it
    * might be attached at runtime after this method is called), we will try to load the registry from the System
    * ClassLoader every time this method is called and then, try to parse its contents.
    */
  def create(warnIfFailed: Boolean = true): Status.Instrumentation = {
    try {
      val kanelaLoaded = JBoolean.parseBoolean(System.getProperty(_kanelaLoadedPropertyName))
      val registryClass = Class.forName(_registryClassName, false, ClassLoader.getSystemClassLoader)

      // We assume that if we were able to load the registry class and the "kanela.loaded" system property are set,
      // then Kanela must be present.
      val present = (registryClass != null) && kanelaLoaded

      val kanelaVersion = Class.forName("kanela.agent.util.BuildInfo", false, ClassLoader.getSystemClassLoader)
        .getMethod("version")
        .invoke(null)
        .asInstanceOf[String]

      val modules = registryClass.getMethod("shareModules")
        .invoke(null)
        .asInstanceOf[JavaList[JavaMap[String, String]]]
        .asScala
        .map(toModule)

      val errors = registryClass.getMethod("shareErrors")
        .invoke(null)
        .asInstanceOf[JavaMap[String, JavaList[Throwable]]]
        .asScala
        .map(toTypeError)
        .toSeq

      Status.Instrumentation(present, Option(kanelaVersion), modules.toSeq, errors)
    } catch {
      case t: Throwable =>

        if(warnIfFailed) {
          t match {
            case _: ClassNotFoundException if warnIfFailed =>
              _logger.warn("Failed to load the instrumentation modules status because the Kanela agent is not available")

            case t: Throwable if warnIfFailed =>
              _logger.warn("Failed to load the instrumentation modules status", t)
          }
        }

        Status.Instrumentation(false, None, Seq.empty, Seq.empty)
    }
  }

  /**
    * Transforms a Map-encoded module into a typed instance. The property names are tied to Kanela's implementation
    */
  private def toModule(map: JavaMap[String, String]): Status.Instrumentation.ModuleInfo = {
    Status.Instrumentation.ModuleInfo (
      map.get("path"),
      map.get("name"),
      map.get("description"),
      JBoolean.parseBoolean(map.get("enabled")),
      JBoolean.parseBoolean(map.get("active"))
    )
  }

  /**
    * Transforms a pair of information into a type error instance. The convention of the first element being the type
    * name is tied to Kanela's implementation
    */
  private def toTypeError(pair: (String, JavaList[Throwable])): TypeError = {
    val (typeName, errors) = pair
    TypeError(typeName, errors.asScala.toSeq)
  }
}
