package kamon.instrumentation.pekko

import kamon.testkit.InitAndStopKamonAfterAll
import org.apache.pekko.testkit.TestKit
import org.scalatest.Suite
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

trait StopSystemAfterAll extends AnyWordSpecLike with InitAndStopKamonAfterAll { this: Suite with TestKit =>

  override protected def afterAll(): Unit = {
    super.afterAll()
    Await.ready(system.terminate(), 20.seconds)
  }
}
