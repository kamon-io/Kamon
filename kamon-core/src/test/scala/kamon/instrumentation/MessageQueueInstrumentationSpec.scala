package kamon.instrumentation

import org.scalatest.WordSpec
import com.codahale.metrics.{ExponentiallyDecayingReservoir, Histogram}
import java.util.concurrent.ConcurrentLinkedQueue
import akka.dispatch.{UnboundedMessageQueueSemantics, QueueBasedMessageQueue, Envelope}
import java.util.Queue
import akka.actor.{ActorSystem, Actor}

class MessageQueueInstrumentationSpec(val actorSystem: ActorSystem) extends WordSpec {
  def this() = this(ActorSystem("MessageQueueInstrumentationSpec"))


  /*"A MonitoredMessageQueue" should {
    "update the related histogram when a message is enqueued" in {
      new PopulatedMessageQueueFixture {

        assert(histogram.getSnapshot.getMax   === 0)

        for(i <- 1 to 3) { enqueueDummyMessage }

        assert(histogram.getCount === 3)
        assert(histogram.getSnapshot.getMax === 3)
        assert(histogram.getSnapshot.getMin === 1)
      }
    }

    "update the related histogram when a message is dequeued" in {
      new PopulatedMessageQueueFixture {
        for(i <- 1 to 3) { enqueueDummyMessage }
        assert(histogram.getSnapshot.getMax   === 3)

        messageQueue.dequeue()
        messageQueue.dequeue()

        assert(histogram.getCount === 5)
        assert(histogram.getSnapshot.getMax === 3)
        assert(histogram.getSnapshot.getMin === 1)
      }
    }
  }

  trait PopulatedMessageQueueFixture {

    val histogram = new Histogram(new ExponentiallyDecayingReservoir())
/*    val delegate = new ConcurrentLinkedQueue[Envelope]() with QueueBasedMessageQueue with UnboundedMessageQueueSemantics {
      final def queue: Queue[Envelope] = this
    }*/
    val messageQueue = new MonitoredMessageQueue(delegate, histogram)

    def enqueueDummyMessage = messageQueue.enqueue(Actor.noSender, Envelope("", Actor.noSender, actorSystem))
  }*/
}
