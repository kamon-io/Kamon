package kamon.trace

import akka.actor.{Props, ActorRef, Actor}
import kamon.trace.UowTracing.{Start, Finish, Rename}
import scala.concurrent.duration.Duration

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
}

case class UowTrace(name: String, segments: Seq[UowSegment])


class UowTraceAggregator(reporting: ActorRef, aggregationTimeout: Duration) extends Actor {
  context.setReceiveTimeout(aggregationTimeout)
  self ! Start()

  var name: Option[String] = None
  var segments: Seq[UowSegment] = Nil

  def receive = {
    case finish: Finish       => segments = segments :+ finish; finishTracing()
    case Rename(newName)      => name = Some(newName)
    case segment: UowSegment  => segments = segments :+ segment
  }

  def finishTracing(): Unit = {
    reporting ! UowTrace(name.getOrElse("UNKNOWN"), segments)
    context.stop(self)
  }
}

object UowTraceAggregator {
  def props(reporting: ActorRef, aggregationTimeout: Duration) = Props(classOf[UowTraceAggregator], reporting, aggregationTimeout)
}