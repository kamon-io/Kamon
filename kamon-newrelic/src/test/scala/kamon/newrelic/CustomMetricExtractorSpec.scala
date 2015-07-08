/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.newrelic

import org.scalatest.{ Matchers, WordSpecLike }

/**
 * @since 21.04.2015
 */
class CustomMetricExtractorSpec extends WordSpecLike with Matchers {

  val cme = CustomMetricExtractor

  "the CustomMetricExtractor" should {
    "have a normalize method" which {
      "is ok with an empty string" in {
        cme.normalize("") should be("")
      }
      "is ok with normal '/'" in {
        cme.normalize("akka/dispatcher/string") should be("akka#dispatcher#string")
      }
      "is ok with multiple '//'" in {
        cme.normalize("akka///dispatcher//string") should be("akka###dispatcher##string")
      }
      "is ok with other special symbols" in {
        cme.normalize("][|*akka*dispatcher|string[") should be("____akka_dispatcher_string_")
      }
    }
  }
}

