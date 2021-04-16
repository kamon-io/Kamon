package kamon.instrumentation.tapir

import akka.actor.ActorSystem
import akka.http.scaladsl.Http

import scala.concurrent.Future
import scala.concurrent.duration._

object TapirAkkaHttpServer {
  implicit val actorSystem: ActorSystem = ActorSystem()

  import actorSystem.dispatcher

  private var server: Future[Http.ServerBinding] = _

  def start = {
    server = Http().newServerAt("localhost", 8080).bindFlow(TestRoutes.routes)
  }

  def stop = server.flatMap(x => x.terminate(5.seconds))

}
