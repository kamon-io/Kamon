package kamon.trace

import java.util.concurrent.TimeUnit

import akka.actor.{ ActorLogging, Props, Actor, ActorRef }
import kamon.{ NanoInterval, RelativeNanoTimestamp }
import kamon.trace.Incubator.{ CheckForCompletedTraces, IncubatingTrace }
import scala.annotation.tailrec
import scala.collection.immutable.Queue
import scala.concurrent.duration._

class Incubator(subscriptions: ActorRef) extends Actor with ActorLogging {
  import context.dispatcher
  val config = context.system.settings.config.getConfig("kamon.trace.incubator")

  val minIncubationTime = new NanoInterval(config.getDuration("min-incubation-time", TimeUnit.NANOSECONDS))
  val maxIncubationTime = new NanoInterval(config.getDuration("max-incubation-time", TimeUnit.NANOSECONDS))
  val checkInterval = config.getDuration("check-interval", TimeUnit.MILLISECONDS)

  val checkSchedule = context.system.scheduler.schedule(checkInterval.millis, checkInterval.millis, self, CheckForCompletedTraces)
  var waitingForMinimumIncubation = Queue.empty[IncubatingTrace]
  var waitingForIncubationFinish = List.empty[IncubatingTrace]

  def receive = {
    case tc: TracingContext ⇒ incubate(tc)
    case CheckForCompletedTraces ⇒
      checkWaitingForMinimumIncubation()
      checkWaitingForIncubationFinish()
  }

  def incubate(tc: TracingContext): Unit =
    waitingForMinimumIncubation = waitingForMinimumIncubation.enqueue(IncubatingTrace(tc, RelativeNanoTimestamp.now))

  @tailrec private def checkWaitingForMinimumIncubation(): Unit = {
    if (waitingForMinimumIncubation.nonEmpty) {
      val it = waitingForMinimumIncubation.head
      if (NanoInterval.since(it.incubationStart) >= minIncubationTime) {
        waitingForMinimumIncubation = waitingForMinimumIncubation.tail

        if (it.tc.shouldIncubate)
          waitingForIncubationFinish = it :: waitingForIncubationFinish
        else
          dispatchTraceInfo(it.tc)

        checkWaitingForMinimumIncubation()
      }
    }
  }

  private def checkWaitingForIncubationFinish(): Unit = {
    waitingForIncubationFinish = waitingForIncubationFinish.filter {
      case IncubatingTrace(context, incubationStart) ⇒
        if (!context.shouldIncubate) {
          dispatchTraceInfo(context)
          false
        } else {
          if (NanoInterval.since(incubationStart) >= maxIncubationTime) {
            log.warning("Trace [{}] with token [{}] has reached the maximum incubation time, will be reported as is.", context.name, context.token)
            dispatchTraceInfo(context);
            false
          } else true
        }
    }
  }

  def dispatchTraceInfo(tc: TracingContext): Unit = subscriptions ! tc.generateTraceInfo

  override def postStop(): Unit = {
    super.postStop()
    checkSchedule.cancel()
  }
}

object Incubator {

  def props(subscriptions: ActorRef): Props = Props(new Incubator(subscriptions))

  case object CheckForCompletedTraces
  case class IncubatingTrace(tc: TracingContext, incubationStart: RelativeNanoTimestamp)
}
