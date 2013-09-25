package kamon.trace

import akka.actor._
import kamon.trace.UowTracing.{Start, Finish, Rename}
import scala.concurrent.duration.Duration
import kamon.trace.UowTracing.Finish
import kamon.trace.UowTracing.Rename
import kamon.trace.UowTrace
import kamon.trace.UowTracing.Start
import scala.Some

sealed trait UowSegment {
  def timestamp: Long
}

trait AutoTimestamp extends UowSegment {
  val timestamp = System.nanoTime
}

object UowTracing {
  case class Start() extends AutoTimestamp
  case class Finish() extends AutoTimestamp
  case class Rename(name: String) extends AutoTimestamp
  case class WebExternal(start: Long, end: Long, host: String) extends AutoTimestamp
}

case class UowTrace(name: String, segments: Seq[UowSegment])


class UowTraceAggregator(reporting: ActorRef, aggregationTimeout: Duration) extends Actor with ActorLogging {
  context.setReceiveTimeout(aggregationTimeout)
  self ! Start()

  var name: Option[String] = None
  var segments: Seq[UowSegment] = Nil

  def receive = {
    case finish: Finish       => segments = segments :+ finish; finishTracing()
    case Rename(newName)      => name = Some(newName)
    case segment: UowSegment  => segments = segments :+ segment
    case ReceiveTimeout       =>
      log.warning("Transaction {} did not complete properly, the recorded segments are: {}", name, segments)
      context.stop(self)
  }

  def finishTracing(): Unit = {
    reporting ! UowTrace(name.getOrElse("UNKNOWN"), segments)
    context.stop(self)
  }
}

object UowTraceAggregator {
  def props(reporting: ActorRef, aggregationTimeout: Duration) = Props(classOf[UowTraceAggregator], reporting, aggregationTimeout)
}