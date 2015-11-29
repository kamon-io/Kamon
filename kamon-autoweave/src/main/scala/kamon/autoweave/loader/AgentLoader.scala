package kamon.autoweave.loader

import java.io.{ File, FileOutputStream, InputStream }
import java.lang.management.ManagementFactory
import java.util.jar.Attributes.Name
import java.util.jar.{ JarEntry, JarOutputStream, Manifest }

import com.sun.tools.attach.VirtualMachine

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
    val vm = VirtualMachine.attach(getPidFromRuntimeMBean)
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
   * Gets bytes from InputStream
   *
   * @param stream
   * The InputStream
   * @return
   * Returns a byte[] representation of given stream
   */
  private def getBytesFromStream(stream: InputStream): Array[Byte] = {
    Stream.continually(stream.read).takeWhile(_ != -1).map(_.toByte).toArray
  }

  private def unqualify(clazz: Class[_]): String = clazz.getName.replace('.', '/') + ".class"
}
