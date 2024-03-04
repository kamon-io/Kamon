package kamon.runtime

import kamon.runtime.attacher.BuildInfo
import net.bytebuddy.agent.ByteBuddyAgent
import org.slf4j.LoggerFactory

import java.lang.management.ManagementFactory
import java.nio.file.{Files, StandardCopyOption}
import scala.util.control.NonFatal

object Attacher {

  private val _instrumentationClassLoaderProp = "kanela.instrumentation.classLoader"

  /**
    * Attaches the Kanela agent to the current JVM. This method will ignore any attempts to attach the agent if it has
    * been attached already.
    */
  def attach(): Unit = {
    val springBootClassLoader = findSpringBootJarLauncherClassLoader()

    if (isKanelaLoaded) {

      // If Kanela has already been loaded and we are running on a Spring Boot application, we might need to reload
      // Kanela to ensure it will use the proper ClassLoader for loading the instrumentations.
      springBootClassLoader.foreach(sbClassLoader => {
        withInstrumentationClassLoader(sbClassLoader)(reloadKanela())
      })

    } else {

      val embeddedAgentFile = Attacher.getClass.getClassLoader.getResourceAsStream(BuildInfo.kanelaAgentJarName)
      val temporaryAgentFile = Files.createTempFile(BuildInfo.kanelaAgentJarName, ".jar")
      Files.copy(embeddedAgentFile, temporaryAgentFile, StandardCopyOption.REPLACE_EXISTING)

      withInstrumentationClassLoader(springBootClassLoader.orNull) {
        ByteBuddyAgent.attach(temporaryAgentFile.toFile, pid())
      }

      try {
        temporaryAgentFile.toFile.deleteOnExit()
      } catch {
        case NonFatal(t) =>
          // We initialize the logger here instead of as a static member to avoid loading
          // Logger-related classes before attaching Kanela to the current JVM.
          LoggerFactory.getLogger(Attacher.getClass)
            .warn("Failed to mark the temporary Kanela Agent file for deletion after exiting the JVM", t)
      }
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
    val hasKanelaClasses =
      try {
        Class.forName("kanela.agent.Kanela", false, ClassLoader.getSystemClassLoader) != null
      } catch {
        case _: Throwable => false
      }

    hasKanelaClasses && isLoadedProperty
  }

  /**
    * Tries to find Spring Boot's classloader, if any. When running a Spring Boot application packaged with the
    * "spring-boot-maven-plugin", a fat jar will be created with all the dependencies in it and a special ClassLoader is
    * used to unpack them when the jar launches. This function will try to find that ClassLoader which should be used to
    * load all Kanela modules.
    */
  private def findSpringBootJarLauncherClassLoader(): Option[ClassLoader] = {
    Option(Thread.currentThread().getContextClassLoader())
      .filter(cl => cl.getClass.getName == "org.springframework.boot.loader.LaunchedURLClassLoader")
  }

  /**
    * Reloads the Kanela agent. This will cause all instrumentation definitions to be dropped and re-initialized.
    */
  private def reloadKanela(): Unit = {

    // We know that if the agent has been attached, its classes are in the System ClassLoader so we try to find
    // the Kanela class from there and call reload on it.
    Class.forName("kanela.agent.Kanela", true, ClassLoader.getSystemClassLoader)
      .getDeclaredMethod("reload")
      .invoke(null)
  }

  private def pid(): String = {
    val jvm = ManagementFactory.getRuntimeMXBean.getName
    jvm.substring(0, jvm.indexOf('@'))
  }

  def withInstrumentationClassLoader[T](classLoader: ClassLoader)(thunk: => T): T = {
    try {
      if (classLoader != null)
        System.getProperties.put(_instrumentationClassLoaderProp, classLoader)
      thunk
    } finally {
      System.getProperties.remove(_instrumentationClassLoaderProp)
    }
  }
}
