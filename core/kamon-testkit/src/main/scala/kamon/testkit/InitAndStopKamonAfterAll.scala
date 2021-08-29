package kamon.testkit

import kamon.Kamon
import org.scalatest.{BeforeAndAfterAll, Suite}

trait InitAndStopKamonAfterAll extends BeforeAndAfterAll { this: Suite =>
  override protected def beforeAll(): Unit = {
    println("Doing the call to start kamon")
    super.beforeAll()
    Kamon.init()
  }

  override protected def afterAll(): Unit = {
    println("Doing the call to STOP kamon")
    super.afterAll()
    Kamon.stop()
  }
}
