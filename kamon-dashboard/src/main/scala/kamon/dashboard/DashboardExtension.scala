package kamon.dashboard

import akka.actor._
import akka.io.IO
import spray.can.Http

object DashboardExtension extends ExtensionId[DashboardExtensionImpl] with ExtensionIdProvider {
  override def lookup = DashboardExtension
  override def createExtension(system: ExtendedActorSystem) = new DashboardExtensionImpl(system)
}

class DashboardExtensionImpl(system: ExtendedActorSystem) extends Extension {
  if("kamon".equalsIgnoreCase(system.name)) {

    val enabled = system.settings.config getBoolean "dashboard.enabled"
    val interface = system.settings.config getString "dashboard.interface"
    val port  = system.settings.config getInt "dashboard.port"

    if(enabled){
        val service = system.actorOf(Props[DashboardServiceActor], "kamon-dashboard-service")
        IO(Http)(system) ! Http.Bind(service, interface, port)
    }
  }
}