package kamon.bundle

import java.lang.management.ManagementFactory
import java.nio.file.{Files, StandardCopyOption}

import net.bytebuddy.agent.ByteBuddyAgent

import scala.util.Try

object Bundle {

  /**
    * Attaches the Kanela agent to the current JVM. This method will ignore any attempts to attach the agent if it has
    * been attached already.
    */
  def attach(): Unit = {
    if(!isKanelaLoaded) {
      val embeddedAgentFile = Bundle.getClass.getClassLoader.getResourceAsStream(BuildInfo.kanelaAgentJarName)
      val temporaryAgentFile = Files.createTempFile(BuildInfo.kanelaAgentJarName, ".jar")
      Files.copy(embeddedAgentFile, temporaryAgentFile, StandardCopyOption.REPLACE_EXISTING)

      ByteBuddyAgent.attach(temporaryAgentFile.toFile, pid())
    }
  }

  /**
    * Tries to determine whether the Kanela agent has been loaded already. Since there are no APIs to determine what
    * agents have been loaded on the current JVM, we rely on two cues that indicate that Kanela is present: first, the
    * "kanela.loaded" System property which is set to "true" when the Kanela agent is started and second, the presence
    * of the Kanela class in the System ClassLoader. None of these two cues are definite proof, but having both of them
    * gives a level of certainty of the Kanela agent being loaded already.
    */
  private def isKanelaLoaded(): Boolean = {
    val isLoadedProperty = java.lang.Boolean.parseBoolean(System.getProperty("kanela.loaded"))
    val hasKanelaClasses = Try {
      Class.forName("kanela.agent.Kanela", false, ClassLoader.getSystemClassLoader) != null
    }.getOrElse(false)

    hasKanelaClasses && isLoadedProperty
  }

  private def pid(): String = {
    val jvm = ManagementFactory.getRuntimeMXBean.getName
    jvm.substring(0, jvm.indexOf('@'))
  }
}
