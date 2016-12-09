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

package kamon.trace

import java.util.function.Supplier

import kamon.testkit.BaseKamonSpec
import kamon.trace.TraceLocal.AvailableToMdc
import kamon.trace.logging.MdcKeysSupport
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.OptionValues
import org.slf4j.MDC

class TraceLocalSpec extends BaseKamonSpec("trace-local-spec") with PatienceConfiguration with OptionValues with MdcKeysSupport {
  val SampleTraceLocalKeyAvailableToMDC = AvailableToMdc("someKey")

  object SampleTraceLocalKey extends TraceLocal.TraceLocalKey[String]

  "the TraceLocal storage" should {
    "allow storing and retrieving values" in {
      Tracer.withContext(newContext("store-and-retrieve-trace-local")) {
        val testString = "Hello World"

        TraceLocal.store(SampleTraceLocalKey)(testString)
        TraceLocal.retrieve(SampleTraceLocalKey).value should equal(testString)
      }
    }

    "return None when retrieving a non existent key" in {
      Tracer.withContext(newContext("non-existent-key")) {
        TraceLocal.retrieve(SampleTraceLocalKey) should equal(None)
      }
    }

    "throws an exception when trying to get a non existent key" in {
      Tracer.withContext(newContext("non-existent-key")) {
        intercept[NoSuchElementException] {
          TraceLocal.get(SampleTraceLocalKey)
        }
      }
    }

    "return the given value when retrieving a non existent key" in {
      Tracer.withContext(newContext("non-existent-key")) {
        TraceLocal.getOrElse(SampleTraceLocalKey, new Supplier[String] { def get = "optionalValue" }) should equal("optionalValue")
      }
    }

    "return None when retrieving a key without a current TraceContext" in {
      TraceLocal.retrieve(SampleTraceLocalKey) should equal(None)
    }

    "be attached to the TraceContext when it is propagated" in {
      val testString = "Hello World"
      val testContext = Tracer.withContext(newContext("manually-propagated-trace-local")) {
        TraceLocal.store(SampleTraceLocalKey)(testString)
        TraceLocal.retrieve(SampleTraceLocalKey).value should equal(testString)
        Tracer.currentContext
      }

      /** No TraceLocal should be available here */
      TraceLocal.retrieve(SampleTraceLocalKey) should equal(None)

      Tracer.withContext(testContext) {
        TraceLocal.retrieve(SampleTraceLocalKey).value should equal(testString)
      }
    }

    "allow retrieve a value from the MDC when was created a key with AvailableToMdc(cool-key)" in {
      Tracer.withContext(newContext("store-and-retrieve-trace-local-and-copy-to-mdc")) {
        val testString = "Hello MDC"

        TraceLocal.store(SampleTraceLocalKeyAvailableToMDC)(testString)
        TraceLocal.retrieve(SampleTraceLocalKeyAvailableToMDC).value should equal(testString)

        withMdc {
          MDC.get("someKey") should equal(testString)
        }
      }
    }

    "allow retrieve a value from the MDC when was created a key with AvailableToMdc.storeForMdc(String, String)" in {
      Tracer.withContext(newContext("store-and-retrieve-trace-local-and-copy-to-mdc")) {
        val testString = "Hello MDC"

        TraceLocal.storeForMdc("someKey", testString)
        TraceLocal.retrieve(SampleTraceLocalKeyAvailableToMDC).value should equal(testString)

        withMdc {
          MDC.get("someKey") should equal(testString)
        }
      }
    }
  }
}
