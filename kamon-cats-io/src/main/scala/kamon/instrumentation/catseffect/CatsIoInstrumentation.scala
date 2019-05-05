package kamon.instrumentation.catseffect
import kamon.Kamon
import kamon.context.{Context, Storage}
import kamon.instrumentation.Mixin.HasContext
import kanela.agent.api.instrumentation.mixin.Initializer
import kanela.agent.libs.net.bytebuddy.asm.Advice
import kanela.agent.scala.KanelaInstrumentation

class CatsIoInstrumentation extends KanelaInstrumentation {

  forTargetType("cats.effect.internals.IOShift$Tick") { builder =>
    builder
      .withMixin(classOf[HasContextMixin])
      .withAdvisorFor(method("run"), classOf[RunMethodAdvisor])
      .build()
  }
}

class HasContextMixin extends HasContext {
  private var _context: Context = _

  @Initializer
  def initialize(): Unit =
    this._context = Kamon.currentContext()

  override def context: Context = _context
}

class RunMethodAdvisor

object RunMethodAdvisor {
  @Advice.OnMethodEnter
  def enter(@Advice.This hasContext: HasContext): Storage.Scope =
    Kamon.storeContext(hasContext.context)

  @Advice.OnMethodExit(onThrowable = classOf[Throwable])
  def exit(@Advice.Enter scope: Storage.Scope): Unit =
    scope.close()
}
