package kamon.testkit

import kamon.Kamon
import org.scalatest.{BeforeAndAfterAll, Suite}

trait InitAndStopKamonAfterAll extends BeforeAndAfterAll { this: Suite =>

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    Kamon.init()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    Kamon.stop()
  }
}
