package kamon.trace

import akka.actor._
import scala.concurrent.duration.Duration
import kamon.trace.UowTracing._

sealed trait UowSegment {
  def id: Long
  def timestamp: Long
}

trait AutoTimestamp extends UowSegment {
  val timestamp = System.nanoTime
}

object UowTracing {
  case class Start(id: Long) extends AutoTimestamp
  case class Finish(id: Long) extends AutoTimestamp
  case class Rename(id: Long, name: String) extends AutoTimestamp
  case class WebExternalStart(id: Long, host: String) extends AutoTimestamp
  case class WebExternalFinish(id: Long) extends AutoTimestamp
  case class WebExternal(id: Long, start: Long, finish: Long, host: String) extends AutoTimestamp
}

case class UowTrace(name: String, segments: Seq[UowSegment])


class UowTraceAggregator(reporting: ActorRef, aggregationTimeout: Duration) extends Actor with ActorLogging {
  context.setReceiveTimeout(aggregationTimeout)

  var name: Option[String] = None
  var segments: Seq[UowSegment] = Nil

  var pendingExternal = List[WebExternalStart]()

  def receive = {
    case finish: Finish       => segments = segments :+ finish; finishTracing()
    case wes: WebExternalStart => pendingExternal = pendingExternal :+ wes
    case finish @ WebExternalFinish(id) => pendingExternal.find(_.id == id).map(start => {
      segments = segments :+ WebExternal(finish.id, start.timestamp, finish.timestamp, start.host)
    })
    case Rename(id, newName)      => name = Some(newName)
    case segment: UowSegment  => segments = segments :+ segment
    case ReceiveTimeout       =>
      log.warning("Transaction {} did not complete properly, the recorded segments are: {}", name, segments)
      context.stop(self)
  }

  def finishTracing(): Unit = {
    reporting ! UowTrace(name.getOrElse("UNKNOWN"), segments)
    println("Recorded Segments: " + segments)
    context.stop(self)
  }
}

object UowTraceAggregator {
  def props(reporting: ActorRef, aggregationTimeout: Duration) = Props(classOf[UowTraceAggregator], reporting, aggregationTimeout)
}