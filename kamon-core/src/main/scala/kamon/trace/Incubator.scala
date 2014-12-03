package kamon.trace

import akka.actor.{ Props, Actor, ActorRef }
import kamon.trace.Incubator.{ CheckForCompletedTraces, IncubatingTrace }
import scala.annotation.tailrec
import scala.collection.immutable.Queue
import scala.concurrent.duration._

class Incubator(subscriptions: ActorRef, maxIncubationNanoTime: Long) extends Actor {
  import context.dispatcher
  val checkSchedule = context.system.scheduler.schedule(100 millis, 100 millis, self, CheckForCompletedTraces)
  var incubating = Queue.empty[IncubatingTrace]

  def receive = {
    case CheckForCompletedTraces ⇒ dispatchCompleted()
    case tc: TracingContext      ⇒ incubating = incubating.enqueue(IncubatingTrace(tc))
  }

  @tailrec private def dispatchCompleted(): Unit = {
    if (incubating.nonEmpty) {
      val it = incubating.head
      if (!it.tc.shouldIncubate || it.incubationNanoTime >= maxIncubationNanoTime) {
        it.tc.generateTraceInfo.map(subscriptions ! _)
        incubating = incubating.tail
        dispatchCompleted()
      }
    }
  }

  override def postStop(): Unit = {
    super.postStop()
    checkSchedule.cancel()
  }
}

object Incubator {

  def props(subscriptions: ActorRef, maxIncubationNanoTime: Long): Props = Props(new Incubator(subscriptions, maxIncubationNanoTime))

  case object CheckForCompletedTraces
  case class IncubatingTrace(tc: TracingContext) {
    private val incubationStartNanoTime = System.nanoTime()
    def incubationNanoTime: Long = System.nanoTime() - incubationStartNanoTime
  }
}
