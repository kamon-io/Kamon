package controllers

import play.api.mvc.{Action, Controller}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent._

object NewRelicExample extends Controller {

  def sayHelloKamon() = Action.async {
    Future {
      play.Logger.info("Say hello to Kamon")
      Ok("Say hello to Kamon")
    }
  }

  def sayHelloNewRelic() = Action.async {
    Future {
      play.Logger.info("Say hello to NewRelic")
      Ok("Say hello to NewRelic")
    }
  }
}
