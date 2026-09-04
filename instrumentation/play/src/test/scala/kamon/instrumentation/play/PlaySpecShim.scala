package kamon.instrumentation.play

import org.scalatestplus.play.WsScalaTestClient
import org.scalatest._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

/**
  * Shim copied from https://github.com/playframework/scalatestplus-play/blob/master/module/src/main/scala/org/scalatestplus/play/PlaySpec.scala.
  *
  * As of 2/20/20, PlaySpec from scalatestplus-play is incompatible with scalatest 3.1.0+
  */
abstract class PlaySpecShim extends AnyWordSpec with Matchers with OptionValues with WsScalaTestClient