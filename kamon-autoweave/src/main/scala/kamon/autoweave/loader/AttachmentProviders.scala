/* =========================================================================================
 * Copyright © 2013-2016 the kamon project <http://kamon.io/>
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

package kamon.autoweave.loader

import java.io.File
import java.net.{ URL, URLClassLoader }
import java.security.{ PrivilegedAction, AccessController }

object AttachmentProviders extends {

  val VirtualMachineTyeName = "com.sun.tools.attach.VirtualMachine"

  sealed trait AttachmentProvider {
    def toolsJarPath: String

    /**
     * Gets the current HotSpotVirtualMachine implementation otherwise a None.
     *
     * @return
     * Returns the HotSpotVirtualMachine implementation of the running JVM.
     */
    def resolve(): Option[Class[_]] = {
      val toolsJar = new File(System.getProperty("java.home").replace('\\', '/') + "/../" + toolsJarPath)
      if (toolsJar.isFile && toolsJar.canRead)
        Some(AccessController.doPrivileged(new ClassLoaderCreationAction(toolsJar)).loadClass(VirtualMachineTyeName))
      else None
    }
  }

  case object JVM extends AttachmentProvider { val toolsJarPath = "../lib/tools.jar" }
  case object JDK extends AttachmentProvider { val toolsJarPath = "lib/tools.jar" }
  case object MAC extends AttachmentProvider { val toolsJarPath = "../Classes/classes.jar" }

  private val providers = Seq(JVM, JDK, MAC)

  private final class ClassLoaderCreationAction(toolsJar: File) extends PrivilegedAction[ClassLoader] {
    override def run(): ClassLoader = new URLClassLoader(Array[URL](toolsJar.toURI.toURL), null)
  }

  def resolve(): Option[Class[_]] = {
    import scala.util.control.Breaks._

    var vmClazz: Option[Class[_]] = None

    breakable {
      for (provider ← providers) {
        val vmClass = provider.resolve()
        if (vmClass.isDefined) {
          vmClazz = vmClass
          break
        }
      }
    }
    vmClazz
  }
}
