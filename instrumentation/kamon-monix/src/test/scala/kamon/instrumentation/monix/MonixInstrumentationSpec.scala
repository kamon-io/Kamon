package kamon.instrumentation.monix

import cats.effect.{ContextShift, Timer}
import kamon.instrumentation.cats.io.AbstractCatsEffectInstrumentationSpec
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global

class MonixInstrumentationSpec extends AbstractCatsEffectInstrumentationSpec[Task]("Monix Task") {

  override implicit def contextShift: ContextShift[Task] = Task.contextShift

  override implicit def timer: Timer[Task] = Task.timer
}