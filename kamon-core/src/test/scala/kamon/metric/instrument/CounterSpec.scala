package kamon.metric.instrument

//import kamon.LogInterceptor
//import kamon.metric.Entity
//import kamon.testkit.DefaultInstrumentFactory
//import org.scalatest.{Matchers, WordSpec}
//import uk.org.lidalia.slf4jext.Level
//import uk.org.lidalia.slf4jtest.TestLoggerFactory
//
//class CounterSpec extends WordSpec with Matchers with LogInterceptor with DefaultInstrumentFactory {
//  implicit val testLogger = TestLoggerFactory.getTestLogger(classOf[LongAdderCounter])
////
////  "a Counter" should {
////
////    "allow unit and bundled increments" in {
////      val counter = buildCounter("unit-increments")
////      counter.increment()
////      counter.increment()
////      counter.increment(40)
////
////      counter.snapshot().value shouldBe(42)
////    }
////
////    "warn the user and ignore attempts to decrement the counter" in {
////      val counter = buildCounter("attempt-to-decrement")
////      counter.increment(100)
////      counter.increment(100)
////      counter.increment(100)
////
////      interceptLog(Level.WARN) {
////        counter.increment(-10L)
////      }.head.getMessage() shouldBe(s"Ignored attempt to decrement counter [attempt-to-decrement] on entity [$defaultEntity]")
////
////      counter.snapshot().value shouldBe(300)
////    }
////
////    "reset the internal state to zero after taking snapshots" in {
////      val counter = buildCounter("reset-after-snapshot")
////      counter.increment()
////      counter.increment(10)
////
////      counter.snapshot().value shouldBe(11)
////      counter.snapshot().value shouldBe(0)
////    }
////  }
//}
