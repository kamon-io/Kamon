import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest.WordSpecLike
import shapeless._

class ExtraSpec extends TestKit(ActorSystem("ExtraSpec")) with WordSpecLike {

  "the Extra pattern helper" should {
    "be constructed from a finite number of types" in {
      Extra.expecting[String :: Int :: HNil].as[Person]
    }
  }

  case class Person(name: String, age: Int)
}

/**
 *    Desired Features:
 *      1. Expect messages of different types, apply a function and forward to some other.
 */

object Extra {
  def expecting[T <: HList] = new Object {
    def as[U <: Product] = ???
  }
}

/*
extra of {
  expect[A] in { actor ! msg}
  expect[A] in { actor ! msg}
} as (A, A) pipeTo (z)*/


