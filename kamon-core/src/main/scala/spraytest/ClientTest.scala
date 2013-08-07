package spraytest

import akka.actor.ActorSystem
import spray.client.pipelining._
import spray.httpx.SprayJsonSupport
import spray.json._
import scala.concurrent.Future
import spray.can.Http
import akka.io.IO

/**
 *    BEGIN JSON Infrastructure
 */
case class Container(data: List[PointOfInterest])
case class Geolocation(latitude: Float, longitude: Float)
case class PointOfInterest(ma: Option[String], a: Option[String], c: String, s: Option[String], geolocation: Geolocation)

object GeoJsonProtocol extends DefaultJsonProtocol {
  implicit val geolocationFormat = jsonFormat2(Geolocation)
  implicit val pointOfInterestFormat = jsonFormat5(PointOfInterest)
  implicit val containerFormat = jsonFormat1(Container)
}
/**   END-OF JSON Infrastructure */






class ClientTest extends App {
  implicit val actorSystem = ActorSystem("spray-client-test")
  import actorSystem.dispatcher


  import GeoJsonProtocol._
  import SprayJsonSupport._


  val actor = IO(Http)

  val pipeline =  sendReceive ~> unmarshal[Container]

  val response = pipeline {
    Get("http://geo.despegar.com/geo-services-web/service/Autocomplete/DESAR/1/0/0/10/0/0/Obelisco")
  } onSuccess {
    case a => {
      println(a)
    }
  }
}





