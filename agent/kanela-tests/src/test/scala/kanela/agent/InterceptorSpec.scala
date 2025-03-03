package kanela.agent

import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice

class InterceptorSpec extends munit.FunSuite {

  test("should delegate method calls to a target object") {
    val targetNumber = new InterceptorSpec.TargetClass().giveMeANumber()
    assertEquals(targetNumber, 42)
  }

  test("should delegate method calls to a target class") {
    val targetNumber = new InterceptorSpec.TargetClass().giveMeAnotherNumber()
    assertEquals(targetNumber, 52)
  }

}

object InterceptorSpec {
  class TargetClass {
    def giveMeANumber(): Int = 32
    def giveMeAnotherNumber(): Int = 32
  }
}

class InterceptorViaInstance {
  def giveMeANumber(): Int = 42
}

class InterceptorViaClass
object InterceptorViaClass {
  def giveMeAnotherNumber(): Int = 52
}

class InterceptorSpecInstrumentation extends InstrumentationBuilder {

  onType("kanela.agent.InterceptorSpec$TargetClass")
    .intercept(method("giveMeANumber"), new InterceptorViaInstance())
    .intercept(method("giveMeAnotherNumber"), classOf[InterceptorViaClass])
}
