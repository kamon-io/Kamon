package kamon.instrumentation.context

import kamon.Kamon
import kamon.context.Context
import kamon.tag.TagSet
import kanela.agent.api.instrumentation.InstrumentationBuilder
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ContextInstrumentationSpec extends AnyWordSpec with Matchers {
  import ContextInstrumentationSpec.{Target, TargetWithInitializer}

  "the HasContext instrumentation" should {
    "make classes extend the HasContext interface in runtime" in {
      (new Target).isInstanceOf[HasContext] shouldBe true
      (new TargetWithInitializer).isInstanceOf[HasContext] shouldBe true
    }

    "allow to update the Context held by the instrumented instances" in {
      val context = Context.of(TagSet.of("key", "value"))
      val target = (new Target).asInstanceOf[HasContext]
      val targetWithInitializer = (new TargetWithInitializer).asInstanceOf[HasContext]

      target.setContext(context)
      targetWithInitializer.setContext(context)

      target.context shouldBe context
      targetWithInitializer.context shouldBe context
    }

    "automatically capture the current context when using the WithCurrentContextInitializer variant" in {
      val context = Context.of(TagSet.of("key", "value"))
      val targetWithInitializer = Kamon.runWithContext(context) {
        (new TargetWithInitializer).asInstanceOf[HasContext]
      }

      targetWithInitializer.context shouldBe context
    }

    "trigger copying of the current context into the instrumented instance with using the CaptureCurrentContextAdvice" in {
      val context = Context.of(TagSet.of("key", "value"))
      val target = new Target
      val targetWithInitializer = new TargetWithInitializer

      target.asInstanceOf[HasContext].context shouldBe Context.Empty
      targetWithInitializer.asInstanceOf[HasContext].context shouldBe Context.Empty

      Kamon.runWithContext(context) {
        target.doSomething()
        targetWithInitializer.doSomething()
      }

      target.asInstanceOf[HasContext].context shouldBe context
      targetWithInitializer.asInstanceOf[HasContext].context shouldBe context
    }

    "use the captured context while running methods advised with the RunWithContextAdvice" in {
      val context = Context.of(TagSet.of("key", "value"))

      val (target, targetWithInitializer) = Kamon.runWithContext(context) {
        (new Target, new TargetWithInitializer)
      }

      target.doWork() shouldBe Context.Empty
      target.doOtherWork() shouldBe Context.Empty

      targetWithInitializer.doWork() shouldBe context
      targetWithInitializer.doOtherWork() shouldBe Context.Empty
    }
  }
}

object ContextInstrumentationSpec {

  class Target {
    def doWork(): Context =
      Kamon.currentContext()

    def doOtherWork(): Context =
      Kamon.currentContext()

    def doSomething(): Unit = {}
  }

  class TargetWithInitializer {
    def doWork(): Context =
      Kamon.currentContext()

    def doOtherWork(): Context =
      Kamon.currentContext()

    def doSomething(): Unit = {}
  }

  class Instrumentation extends InstrumentationBuilder {

    onType("kamon.instrumentation.context.ContextInstrumentationSpec$Target")
      .mixin(classOf[HasContext.Mixin])
      .advise(method("doSomething"), CaptureCurrentContextOnExit)
      .advise(method("doWork"), InvokeWithCapturedContext)

    onType("kamon.instrumentation.context.ContextInstrumentationSpec$TargetWithInitializer")
      .mixin(classOf[HasContext.MixinWithInitializer])
      .advise(method("doSomething"), CaptureCurrentContextOnExit)
      .advise(method("doWork"), InvokeWithCapturedContext)

  }
}
