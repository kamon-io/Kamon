package kamon

import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.description.method.MethodDescription
import kanela.agent.libs.net.bytebuddy.matcher.ElementMatcher.Junction

package object instrumentation {

  trait AdviseWithCompanionObject {

    /**
      * Applies an advice to the provided method(s). The advice implementation is expected to be a companion object with
      * a method annotated with @Advice.OnMethodEnter and/or a method annotated with @Advice.OnMethodExit. Note that the
      * underlying implementation is expecting a class with static methods, which can only be provided by plain companion
      * objects; any nested companion object will not be appropriately picked up.
      */
    def advise[A](method: Junction[MethodDescription], advice: A)(implicit singletonEvidence: A <:< Singleton): InstrumentationBuilder.Target
  }

  implicit def adviseWithCompanionObject(target: InstrumentationBuilder.Target) = new AdviseWithCompanionObject {

    override def advise[A](method: Junction[MethodDescription], advice: A)(implicit singletonEvidence: A <:< Singleton): InstrumentationBuilder.Target = {
      // Companion object instances always have the '$' sign at the end of their class name, we must remove it to get
      // to the class that exposes the static methods.
      val className = advice.getClass.getName.dropRight(1)
      val adviseClass = Class.forName(className, true, advice.getClass.getClassLoader)

      target.advise(method, adviseClass)
    }
  }
}
