package kamon.instrumentation.monix

import cats.effect.{ContextShift, Timer}
import kamon.instrumentation.cats.io.AbstractCatsEffectInstrumentationSpec
import monix.bio.{IO, Task}
import monix.execution.Scheduler.Implicits.global

class MonixBIOInstrumentationSpec extends AbstractCatsEffectInstrumentationSpec[Task]("Monix Bifunctor IO") {

  override implicit def contextShift: ContextShift[Task] = IO.contextShift

  override implicit def timer: Timer[Task] = IO.timer
}