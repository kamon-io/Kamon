package kamon.instrumentation.akka

import java.time
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import org.openjdk.jmh.annotations._

import scala.concurrent.duration._
import scala.concurrent.Await

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.AverageTime))
@Fork(2)
@Warmup(iterations = 4, time = 5)
@Measurement(iterations = 10, time = 5)
class ActorCreationBench {
  val system = ActorSystem("ActorCreationBench")
  val activeActorsCount = new AtomicLong()
  val props = Props(new DummyCreationActor(activeActorsCount))
  val baseName = "creation-test-actor"
  var lastIndex = 0

  @Benchmark
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def createActor(): ActorRef = {
    lastIndex += 1
    system.actorOf(props, baseName + lastIndex)
  }

  @TearDown(Level.Iteration)
  def cleanupActors(): Unit = {
    system.actorSelection("/user/*").tell(PoisonPill, Actor.noSender)
    while(activeActorsCount.get() > 0) { Thread.sleep(50) }
  }

  @TearDown
  def stopActorSystem(): Unit = {
    system.terminate()
    Await.ready(system.whenTerminated, 30 seconds)
  }
}

class DummyCreationActor(activeCounter: AtomicLong) extends Actor {
  activeCounter.incrementAndGet()

  override def receive: Receive = {
    case _ =>
  }

  override def postStop(): Unit = {
    super.postStop()
    activeCounter.decrementAndGet()
  }
}
