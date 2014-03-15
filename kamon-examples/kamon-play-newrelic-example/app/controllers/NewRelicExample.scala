/* ===================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */
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
