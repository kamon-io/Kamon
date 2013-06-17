package akka.instrumentation

import org.scalatest.{Matchers, WordSpec}
import akka.actor.{Actor, Props, ActorSystem}
import kamon.metric.Metrics._
import scala.collection.JavaConverters._
import akka.testkit.TestActorRef


class ActorInstrumentationSpec extends WordSpec with Matchers {
  implicit val system = ActorSystem()
  import system._

  val echoRef = actorOf(Props(new EchoActor), "Echo-Actor")
  val meterForEchoActor = "meter-for-akka://default/user/Echo-Actor"
  val totalMessages = 1000

  "an instrumented Actor" should  {
    "send a message and record a metric on the Metrics Registry with the number of sent messages" in {

      val echoActor = TestActorRef[EchoActor]



      (1 to totalMessages).foreach {x:Int =>
        echoActor ! s"Message ${x}"
      }

      //val messages = registry.getMeters.asScala.get(meterForEchoActor).get.getCount

      //messages should equal(totalMessages)
    }
  }

}

class EchoActor extends Actor {
  def receive = {
    case msg ⇒ sender ! msg
  }
}


