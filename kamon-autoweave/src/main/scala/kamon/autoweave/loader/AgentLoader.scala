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
import java.util
import java.util.jar.Attributes.Name
import java.util.jar.{ JarEntry, JarOutputStream, Manifest }

import com.sun.tools.attach.spi.AttachProvider
import com.sun.tools.attach.{ VirtualMachine, VirtualMachineDescriptor }
import sun.tools.attach._

import scala.util.control.NoStackTrace
import scala.util.{ Failure, Success, Try }

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
   * @param agent The main agent class.
   * @param resources Array of classes to be included with agent.
   */
  def attachAgentToJVM(agent: Class[_], resources: Seq[Class[_]] = Seq.empty): Unit = {
    val vm = attachToRunningJVM()
    vm.loadAgent(generateAgentJar(agent, resources).getAbsolutePath)
    vm.detach()
  }

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

  /**
   * Gets the current HotSpotVirtualMachine implementation otherwise a failure.
   *
   * @return
   * Returns the HotSpotVirtualMachine implementation of the running JVM.
   */
  private def findVirtualMachineImplementation(): Try[Class[_ <: HotSpotVirtualMachine]] = System.getProperty("os.name") match {
    case os if os.startsWith("Windows") ⇒ Success(classOf[WindowsVirtualMachine])
    case os if os.startsWith("Mac OS X") ⇒ Success(classOf[BsdVirtualMachine])
    case os if os.startsWith("Solaris") ⇒ Success(classOf[SolarisVirtualMachine])
    case os if os.startsWith("Linux") || os.startsWith("LINUX") ⇒ Success(classOf[LinuxVirtualMachine])
    case other ⇒ Failure(new RuntimeException(s"Cannot use Attach API on unknown OS: $other") with NoStackTrace)
  }

  /**
   * Attach to the running JVM.
   *
   * @return
   * Returns the attached VirtualMachine
   */
  private def attachToRunningJVM(): VirtualMachine = {
    val AttachProvider = new AttachProvider() {
      override def name(): String = null
      override def `type`(): String = null
      override def attachVirtualMachine(id: String): VirtualMachine = null
      override def listVirtualMachines(): util.List[VirtualMachineDescriptor] = null
    }

    findVirtualMachineImplementation() match {
      case Success(vmClass) ⇒
        val pid = getPidFromRuntimeMBean
        // This is only done with Reflection to avoid the JVM pre-loading all the XyzVirtualMachine classes.
        val vmConstructor = vmClass.getConstructor(classOf[AttachProvider], classOf[String])
        val newVM = vmConstructor.newInstance(AttachProvider, pid)
        newVM.asInstanceOf[VirtualMachine]
      case Failure(reason) ⇒ throw reason
    }
  }
}

