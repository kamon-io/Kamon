package akka.instrumentation

import org.scalatest.WordSpec
import org.scalatest.matchers.{ShouldMatchers, MustMatchers}
import akka.actor.{Actor, Props, ActorSystem}
import kamon.metric.Metrics._
import scala.collection.JavaConverters._


class ActorInstrumentationSpec extends WordSpec with MustMatchers with ShouldMatchers {
  val system = ActorSystem()
  import system._

  val echoRef = actorOf(Props(new EchoActor), "Echo-Actor")
  val meterForEchoActor = "meter-for-akka://default/user/Echo-Actor"
  val totalMessages = 1000

  "an instrumented Actor" should  {
    "send a message and record a metric on the Metrics Registry with the number of sent messages" in {

      (1 to totalMessages).foreach {x:Int =>
        echoRef ! s"Message ${x}"
      }

      //to ensure that all messages was received
      Thread.sleep(1000)

      val messages = registry.getMeters.asScala.get(meterForEchoActor).get.getCount

      messages should equal(totalMessages)
    }
  }

}

class EchoActor extends Actor {
  def receive = {
    case msg ⇒ sender ! msg
  }
}


