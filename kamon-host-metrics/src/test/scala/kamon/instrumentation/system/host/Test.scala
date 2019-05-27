package kamon.instrumentation.system.host

import java.util.UUID
import java.util.concurrent.{Executors, ThreadLocalRandom}

import kamon.Kamon

object Test extends App {
  Kamon.loadModules()


  val pool = Executors.newWorkStealingPool(32)

  def executeShit(): Unit = {
    pool.submit(new Runnable {
      override def run(): Unit = {
        while(true) {
          ThreadLocalRandom.current().nextBytes(Array.ofDim(32))
        }
      }
    })
  }

  executeShit()
//  executeShit()
//  executeShit()
//  executeShit()
//  executeShit()
//  executeShit()
//  executeShit()
//  executeShit()

}
