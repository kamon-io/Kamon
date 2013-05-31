package akka.instrumentation

import org.scalatest.WordSpec
import org.scalatest.matchers.{ShouldMatchers, MustMatchers}
import akka.actor.{Actor, Props, ActorSystem}
import kamon.metric.Metrics
import com.codahale.metrics.Meter
import scala.collection.JavaConverters._


class ActorInstrumentationSpec extends WordSpec with MustMatchers with ShouldMatchers {
  import Metrics.metricsRegistry._
  val system = ActorSystem()

  import system._
  val echoRef = actorOf(Props(new EchoActor), "Echo-Actor")

  "a instrumented Actor" should  {
    "send a message and record a metric on the Metrics Registry and count messages" in {

      echoRef ! "Message 1"
      echoRef ! "Message 2"
      echoRef ! "Message 3"
      echoRef ! "Message 4"
      echoRef ! "Message 5"
      echoRef ! "Message 6"

      val meter = getMeters.asScala.filterKeys(_.toLowerCase.contains("Echo-Actor".toLowerCase())).collectFirst{case pair:(String, Meter) => pair._2.getCount}.get

      meter should equal(6)
    }
  }

}

class EchoActor extends Actor {
  def receive = {
    case msg ⇒ sender ! msg
  }
}


