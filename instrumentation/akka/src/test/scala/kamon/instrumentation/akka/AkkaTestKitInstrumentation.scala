package kamon.instrumentation.akka

import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice

import scala.annotation.static

class AkkaTestKitInstrumentation extends InstrumentationBuilder {

  /**
    * We believe that tests fail randomly because every now and then the tests thread receives a message from one of the
    * echo actors and continues processing before the execution of the receive function on the echo actor's thread
    * finishes and metrics are recorded. This instrumentation delays the waiting on the test thread to get better
    * chances that the echo actor receive finishes.
    */
  onSubTypesOf("akka.testkit.TestKitBase")
    .advise(method("receiveOne"), DelayReceiveOne)
}

class DelayReceiveOne
object DelayReceiveOne {

  @Advice.OnMethodExit(suppress = classOf[Throwable])
  @static def exit(): Unit =
    Thread.sleep(5)

}
