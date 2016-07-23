package kamon.akka.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer

import scala.concurrent.Future
import scala.io.StdIn

object Client extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  implicit val executionContext = system.dispatcher

  val responseFuture: Future[HttpResponse] =
    Http().singleRequest(HttpRequest(uri = "http://akka.io"))

  StdIn.readLine() // let it run until user presses return
  responseFuture // trigger unbinding from the port
    .onComplete(a ⇒ {
      println(a)
      system.terminate()
    }) // and shutdown when done
}
