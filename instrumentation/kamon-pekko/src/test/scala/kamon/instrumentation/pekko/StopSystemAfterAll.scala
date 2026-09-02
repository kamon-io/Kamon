package kamon.instrumentation.pekko

import kamon.testkit.InitAndStopKamonAfterAll
import org.apache.pekko.testkit.TestKit
import org.scalatest.Suite
import org.scalatest.wordspec.AnyWordSpecLike

trait StopSystemAfterAll extends AnyWordSpecLike with InitAndStopKamonAfterAll { this: Suite with TestKit =>

  override protected def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }
}
