package kamon.instrumentation.pekko.connectors.kafka

import java.lang.reflect.Modifier
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ProduceWithEnvelopeContextSpec extends AnyWordSpec with Matchers {

  "the ProduceWithEnvelopeContext advice" should {
    "expose static advice methods that keep their parameter annotations" in {
      val adviceClass = Class.forName("kamon.instrumentation.pekko.connectors.kafka.ProduceWithEnvelopeContext")
      val parameterAnnotations = adviceClass.getDeclaredMethods.toList
        .filter(method => Modifier.isStatic(method.getModifiers))
        .map(method =>
          method.getName -> method.getParameterAnnotations.toList.map(_.toList.map(_.annotationType.getSimpleName))
        )
        .toMap

      parameterAnnotations shouldBe Map(
        "enter" -> List(List("Argument")),
        "exit" -> List(List("Enter"))
      )
    }
  }
}
