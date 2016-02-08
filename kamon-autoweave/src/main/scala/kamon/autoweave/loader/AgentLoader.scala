/* =========================================================================================
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

package kamon.autoweave.loader

import java.io.{ File, FileOutputStream, InputStream }
import java.lang.management.ManagementFactory
import java.util.jar.Attributes.Name
import java.util.jar.{ JarEntry, JarOutputStream, Manifest }

import scala.util.control.NoStackTrace

object AgentLoader {

  /**
   * Gets the current JVM PID
   *
   * @return Returns the PID
   */

  private def getPidFromRuntimeMBean: String = {
    val jvm = ManagementFactory.getRuntimeMXBean.getName
    val pid = jvm.substring(0, jvm.indexOf('@'))
    pid
  }

  /**
   * Loads an agent into a JVM.
   *
   * @param agent     The main agent class.
   * @param resources Array of classes to be included with agent.
   */
  def attachAgentToJVM(agent: Class[_], resources: Seq[Class[_]] = Seq.empty): Unit = attachToRunningJVM(agent, resources)

  /**
   * Java variant
   *
   * @param agent
   */
  def attachAgentToJVM(agent: Class[_]): Unit = attachAgentToJVM(agent, Seq.empty)

  /**
   * Generates a temporary agent file to be loaded.
   *
   * @param agent     The main agent class.
   * @param resources Array of classes to be included with agent.
   * @return Returns a temporary jar file with the specified classes included.
   */
  private def generateAgentJar(agent: Class[_], resources: Seq[Class[_]]): File = {
    val jarFile = File.createTempFile("agent", ".jar")
    jarFile.deleteOnExit()

    val manifest = new Manifest()
    val mainAttributes = manifest.getMainAttributes
    // Create manifest stating that agent is allowed to transform classes
    mainAttributes.put(Name.MANIFEST_VERSION, "1.0")
    mainAttributes.put(new Name("Agent-Class"), agent.getName)
    mainAttributes.put(new Name("Can-Retransform-Classes"), "true")
    mainAttributes.put(new Name("Can-Redefine-Classes"), "true")
    mainAttributes.put(new Name("Can-Set-Native-Method-Prefix"), "true")

    val jos = new JarOutputStream(new FileOutputStream(jarFile), manifest)

    jos.putNextEntry(new JarEntry(agent.getName.replace('.', '/') + ".class"))

    jos.write(getBytesFromStream(agent.getClassLoader.getResourceAsStream(unqualify(agent))))
    jos.closeEntry()

    for (clazz ← resources) {
      val name = unqualify(clazz)
      jos.putNextEntry(new JarEntry(name))
      jos.write(getBytesFromStream(clazz.getClassLoader.getResourceAsStream(name)))
      jos.closeEntry()
    }

    jos.close()
    jarFile
  }

  /**
   * Attach to the running JVM.
   *
   * @return
   * Returns the attached VirtualMachine
   */
  private def attachToRunningJVM(agent: Class[_], resources: Seq[Class[_]]): Unit = {
    AttachmentProviders.resolve() match {
      case Some(virtualMachine) ⇒
        val virtualMachineInstance = virtualMachine.getDeclaredMethod("attach", classOf[String]).invoke(null, getPidFromRuntimeMBean)
        virtualMachine.getDeclaredMethod("loadAgent", classOf[String], classOf[String])
          .invoke(virtualMachineInstance, generateAgentJar(agent, resources).getAbsolutePath, "")
        virtualMachine.getDeclaredMethod("detach").invoke(virtualMachineInstance)
      case None ⇒ throw new RuntimeException(s"Error trying to use Attach API") with NoStackTrace
    }
  }

  /**
   * Gets bytes from InputStream.
   *
   * @param stream
   * The InputStream.
   * @return
   * Returns a byte[] representation of given stream.
   */
  private def getBytesFromStream(stream: InputStream): Array[Byte] = {
    Stream.continually(stream.read).takeWhile(_ != -1).map(_.toByte).toArray
  }

  private def unqualify(clazz: Class[_]): String = clazz.getName.replace('.', '/') + ".class"
}

