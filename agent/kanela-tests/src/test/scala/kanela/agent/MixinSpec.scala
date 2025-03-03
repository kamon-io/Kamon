package kanela.agent

import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice

class MixinSpec extends munit.FunSuite {

  test("should add a mixin to a class") {
    val targetInstance = new TargetClassForMixin()

    assert(targetInstance.isInstanceOf[GetAnotherNumber])
    assertEquals(targetInstance.giveMeANumber(), 32)
    assertEquals(targetInstance.asInstanceOf[GetAnotherNumber].getAnotherNumber(), 42)
  }

}

class TargetClassForMixin {
  def giveMeANumber(): Int = 32
}

trait GetAnotherNumber {
  def getAnotherNumber(): Int
}

object GetAnotherNumber {
  class Mixin extends GetAnotherNumber {
    def getAnotherNumber(): Int = 42
  }
}

class MixinSpecInstrumentation extends InstrumentationBuilder {

  onType("kanela.agent.TargetClassForMixin")
    .mixin(classOf[GetAnotherNumber.Mixin])
}
