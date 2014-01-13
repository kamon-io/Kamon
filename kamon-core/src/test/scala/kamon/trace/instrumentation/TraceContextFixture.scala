package kamon.trace.instrumentation

import scala.util.Random
import kamon.trace.TraceContext
import akka.actor.Actor

trait TraceContextFixture {
  val random = new Random(System.nanoTime)
  val testTraceContext = Some(TraceContext(Actor.noSender, random.nextInt))
}