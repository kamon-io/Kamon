package akka

import actor.ActorSystemImpl
import scala.concurrent.forkjoin.ForkJoinPool
import scala.concurrent.duration._
import akka.dispatch.Mailbox
import scala._

object Tracer {
  protected[this] var mailboxes:List[Mailbox] = Nil
  protected[this] var tracerActorSystem: ActorSystemImpl = _
  protected[this] var forkJoinPool:ForkJoinPool = _

  def collectPool(pool: ForkJoinPool) = forkJoinPool = pool
  def collectActorSystem(actorSystem: ActorSystemImpl)  = tracerActorSystem = actorSystem
  def collectMailbox(mb: akka.dispatch.Mailbox)  =  mailboxes ::=  mb

  def start():Unit ={
    implicit val dispatcher = tracerActorSystem.dispatcher

    tracerActorSystem.scheduler.schedule(6 seconds, 5 second, new MailboxSenderMetrics(mailboxes))
    tracerActorSystem.scheduler.schedule(7 seconds, 5 second, new PoolMetricsSender(forkJoinPool))
  }
}