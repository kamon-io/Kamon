package kamon.instrumentation.akka.http

trait VersionFiltering {
  def onAkkaHttp(version: String)(block: => Unit): Unit = {
    if(akka.http.Version.current.startsWith(version))
      block
  }
}
