package kamon.spray

import org.scalatest.{ ShouldMatchers, WordSpecLike }
import spray.routing.Directives
import spray.testkit.ScalatestRouteTest
import kamon.trace.Trace
import spray.http.HttpHeaders.RawHeader

class UowSpec
    extends WordSpecLike
    with ScalatestRouteTest
    with ShouldMatchers
    with Directives
    with UowDirectives {

  val testRoute =
    path("uow") {
      uow {
        respondWithUow {
          complete("uow")
        }
      }
    }

  "the UOW directives" should {
    "add an auto-generated UOW if none was provided" in {
      Trace.start("test")
      Get("/uow") ~> testRoute ~> check {
        val uow = header("X-UOW").getOrElse(fail("No UOW present in response"))
        uow.value should fullyMatch regex """.*-\d+"""
      }
    }
    "add an auto-generated UOW if the one provided is blank" in {
      val testUow = "   "
      Trace.start("test")
      Get("/uow").withHeaders(RawHeader("X-UOW", testUow)) ~> testRoute ~> check {
        val uow = header("X-UOW").getOrElse(fail("No UOW present in response"))
        uow.value should fullyMatch regex """.*-\d+"""
      }
    }
    "respond with the same UOW that was provided, if not blank" in {
      val testUow = "test-uow-directives"
      Trace.start("test")
      Get("/uow").withHeaders(RawHeader("X-UOW", testUow)) ~> testRoute ~> check {
        val uow = header("X-UOW").getOrElse(fail("No UOW present in response"))
        uow.value should be(testUow)
      }
    }
  }
}
