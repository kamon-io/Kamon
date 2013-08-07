package kamon

import akka.actor.Actor
import java.util.UUID

class TransactionPublisher extends Actor {

  def receive = {
    case FullTransaction(id, entries) => println(s"I got a full tran: $id - $entries")
  }

}


case class FullTransaction(id: UUID, entries: List[TraceEntry])
