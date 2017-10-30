package kamon.play

import kamon.Kamon
import kamon.testkit.{Reconfigure, TestSpanReporter}
import kamon.util.Registration
import org.scalatest.BeforeAndAfterAll

trait SpanReporter extends Reconfigure {
  self:BeforeAndAfterAll=>

  @volatile var registration: Registration = _
  val reporter = new TestSpanReporter()

  override protected def beforeAll(): Unit = {
    enableFastSpanFlushing()
    sampleAlways()
    registration = Kamon.addReporter(reporter)
  }

  override protected def afterAll(): Unit = {
    registration.cancel()
  }

}
