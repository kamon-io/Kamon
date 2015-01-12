package kamon.metric

import akka.actor.{ Props, Actor, ActorRef }
import kamon.Kamon
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.TickMetricSnapshotBuffer.FlushBuffer
import kamon.metric.instrument.CollectionContext
import kamon.util.MapMerge

import scala.concurrent.duration.FiniteDuration

class TickMetricSnapshotBuffer(flushInterval: FiniteDuration, receiver: ActorRef) extends Actor {
  import MapMerge.Syntax

  val flushSchedule = context.system.scheduler.schedule(flushInterval, flushInterval, self, FlushBuffer)(context.dispatcher)
  val collectionContext: CollectionContext = Kamon(Metrics)(context.system).buildDefaultCollectionContext

  def receive = empty

  def empty: Actor.Receive = {
    case tick: TickMetricSnapshot ⇒ context become (buffering(tick))
    case FlushBuffer              ⇒ // Nothing to flush.
  }

  def buffering(buffered: TickMetricSnapshot): Actor.Receive = {
    case TickMetricSnapshot(_, to, tickMetrics) ⇒
      val combinedMetrics = buffered.metrics.merge(tickMetrics, (l, r) ⇒ l.merge(r, collectionContext))
      val combinedSnapshot = TickMetricSnapshot(buffered.from, to, combinedMetrics)

      context become (buffering(combinedSnapshot))

    case FlushBuffer ⇒
      receiver ! buffered
      context become (empty)

  }

  override def postStop(): Unit = {
    flushSchedule.cancel()
    super.postStop()
  }
}

object TickMetricSnapshotBuffer {
  case object FlushBuffer

  def props(flushInterval: FiniteDuration, receiver: ActorRef): Props =
    Props[TickMetricSnapshotBuffer](new TickMetricSnapshotBuffer(flushInterval, receiver))
}
