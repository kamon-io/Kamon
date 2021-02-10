package kamon.instrumentation.akka.http

trait VersionFiltering {
  def onAkkaHttp(version: String)(block: => Unit): Unit = {
    val akkaHttpVersion = getVersion

    if (akkaHttpVersion.exists(_.startsWith(version)))
      block
  }

  private def getVersion: Option[String] = {
    try {
      Option(akka.http.Version.current)
    } catch {
      case _: Throwable => None
    }
  }
}
