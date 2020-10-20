package kamon.instrumentation.cats.io

import cats.effect.{ContextShift, IO, Timer}

import scala.concurrent.ExecutionContext.global

class CatsIOInstrumentationSpec extends AbstractCatsEffectInstrumentationSpec[IO]("IO") {

  override implicit def contextShift: ContextShift[IO] = IO.contextShift(global)

  override implicit def timer: Timer[IO] = IO.timer(global)
}