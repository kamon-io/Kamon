package kamon.actor

import akka.actor.{ActorRef, Actor}

trait EnhancedActor extends Actor {
  protected[this] var transactionContext: TransactionContext = _

  final def receive = {
    case a: Any => {
      a match {
        case ContextAwareMessage(ctx, message) => {
          transactionContext = ctx
          println(s"Actor ${self.path.toString}. Current context: ${transactionContext}")
          wrappedReceive(message)
        }
        case message: Any => wrappedReceive(message)
      }
    }
  }




  def wrappedReceive: Receive


  def superTell(target: ActorRef, message: Any) = {
    target.tell(ContextAwareMessage(transactionContext, message), self)
  }

}


case class ContextAwareMessage(context: TransactionContext, message: Any)


case class TransactionContext(id: Long, entries: List[ContextEntry]) {
  def append(entry: ContextEntry) = this.copy(entries = entry :: this.entries)
}
sealed trait ContextEntry

case class DeveloperComment(comment: String) extends ContextEntry

case class MessageExecutionTime(actorPath: String, begin: Long, end: Long) extends ContextEntry

