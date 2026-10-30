package kamon.instrumentation.context

import java.lang.reflect.Modifier
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CaptureCurrentTimestampSpec extends AnyWordSpec with Matchers {

  "the CaptureCurrentTimestamp advices" should {
    "expose static advice methods that keep their parameter annotations" in {
      staticMethodParameterAnnotations("kamon.instrumentation.context.CaptureCurrentTimestampOnEnter") shouldBe
      Map("enter" -> List(List("This")))

      staticMethodParameterAnnotations("kamon.instrumentation.context.CaptureCurrentTimestampOnExit") shouldBe
      Map("exit" -> List(List("This")))
    }
  }

  private def staticMethodParameterAnnotations(className: String): Map[String, List[List[String]]] =
    Class.forName(className).getDeclaredMethods.toList
      .filter(method => Modifier.isStatic(method.getModifiers))
      .map(method =>
        method.getName -> method.getParameterAnnotations.toList.map(_.toList.map(_.annotationType.getSimpleName))
      )
      .toMap
}
