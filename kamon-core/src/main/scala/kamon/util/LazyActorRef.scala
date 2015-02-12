package kamon.util

import java.util
import java.util.concurrent.ConcurrentLinkedQueue

import akka.actor.{ Actor, ActorRef }
import org.HdrHistogram.WriterReaderPhaser

import scala.annotation.tailrec

/**
 *  A LazyActorRef accumulates messages sent to an actor that doesn't exist yet. Once the actor is created and
 *  the LazyActorRef is pointed to it, all the accumulated messages are flushed and any new message sent to the
 *  LazyActorRef will immediately be sent to the pointed ActorRef.
 *
 *  This is intended to be used during Kamon's initialization where some components need to use ActorRefs to work
 *  (like subscriptions and the trace incubator) but our internal ActorSystem is not yet ready to create the
 *  required actors.
 */
class LazyActorRef {
  private val _refPhaser = new WriterReaderPhaser
  private val _backlog = new ConcurrentLinkedQueue[(Any, ActorRef)]()
  @volatile private var _target: Option[ActorRef] = None

  def tell(message: Any)(implicit sender: ActorRef = Actor.noSender): Unit = {
    val criticalEnter = _refPhaser.writerCriticalSectionEnter()
    try {
      _target.map(_.tell(message, sender)) getOrElse {
        _backlog.add((message, sender))
      }

    } finally { _refPhaser.writerCriticalSectionExit(criticalEnter) }
  }

  def point(target: ActorRef): Unit = {
    @tailrec def drain(q: util.Queue[(Any, ActorRef)]): Unit = if (!q.isEmpty) {
      val (msg, sender) = q.poll()
      target.tell(msg, sender)
      drain(q)
    }

    try {
      _refPhaser.readerLock()

      if (_target.isEmpty) {
        _target = Some(target)
        _refPhaser.flipPhase(1000L)
        drain(_backlog)

      } else sys.error("A LazyActorRef cannot be pointed more than once.")
    } finally { _refPhaser.readerUnlock() }
  }
}
