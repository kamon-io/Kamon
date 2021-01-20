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
    if(akkaVersion().exists(av => version.exists(v => av.startsWith(v))))
      block
  }

  /**
    * Runs the code block if compatible Akka version is known to be present.
    * example major: 2.6, minor: 8 would run the code block on versions
    * 2.6.0-2.6.8
    */
  def untilAkkaVersion(major: String, minor: Int)(block: => Unit): Unit = {
    val version = akkaVersion()
    val lastSeparator = version.map(_.lastIndexOf('.'))
    val versions: Option[(String, String)] = version.map(_.splitAt(lastSeparator.getOrElse(0)))

    if(versions.exists(x => x._1.startsWith(major) && minor >= x._2.drop(1).toInt)) {
      block
    }
  }

  /**
    * Runs the code block if compatible Akka version is known to be present.
    * example major: 2.6, minor: 8 would run the code block on versions 2.6.8 and higher
    */
  def afterAkkaVersion(major: String, minor: Int)(block: => Unit): Unit = {
    val version = akkaVersion()
    val lastSeparator = version.map(_.lastIndexOf('.'))
    val versions: Option[(String, String)] = version.map(_.splitAt(lastSeparator.getOrElse(0)))

    if(versions.exists(x => x._1.startsWith(major) && minor <= x._2.drop(1).toInt)) {
      block
    }
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
