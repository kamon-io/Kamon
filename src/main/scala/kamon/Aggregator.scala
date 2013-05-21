package kamon

import akka.actor.Actor
import scala.collection.mutable

class Aggregator extends Actor {

  val parts = mutable.LinkedList[TraceEntry]()

  def receive = {
    case ContextPart(ctx) => println("registering context information")
    case FinishAggregation() => println("report to newrelic")
  }

}

case class ContextPart(context: TraceContext)
case class FinishAggregation()
