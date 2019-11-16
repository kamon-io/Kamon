package kamon.instrumentation.akka.instrumentations

/**
  * Helps applying certain instrumentations only if Akka or a specific version of Akka is present.
  */
trait VersionFiltering {

  /**
    * Runs the code block if Akka is known to be present.
    */
  def onAkka(block: => Unit): Unit = {
    if(akkaVersion().nonEmpty)
      block
  }

  /**
    * Runs the code block if a version of Akka starting with the provided version is known to be present.
    */
  def onAkka(version: String*)(block: => Unit): Unit = {
    if(akkaVersion().filter(av => version.exists(v => av.startsWith(v))).isDefined)
      block
  }

  // This should only succeed when Akka is on the classpath.
  private def akkaVersion(): Option[String] = {
    try {
      Option(akka.Version.current)
    } catch {
      case _: Throwable => None
    }
  }
}
