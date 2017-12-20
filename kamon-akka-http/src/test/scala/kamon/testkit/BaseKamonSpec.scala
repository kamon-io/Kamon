/*
 * =========================================================================================
 * Copyright © 2013-2016 the kamon project <http://kamon.io/>
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

package kamon.testkit

import akka.http.scaladsl.model.headers.RawHeader
import com.typesafe.config.ConfigValueFactory
import kamon.Kamon
import kamon.trace.SpanCodec.B3
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

abstract class BaseKamonSpec extends WordSpecLike with Matchers with BeforeAndAfterAll {

  def traceIdHeader(id: String): RawHeader = RawHeader(B3.Headers.TraceIdentifier, id)
  def spanIdHeader(id: String): RawHeader = RawHeader(B3.Headers.SpanIdentifier, id)
  def parentSpanIdHeader(id: String): RawHeader = RawHeader(B3.Headers.ParentSpanIdentifier, id)

  def updateAndReloadConfig(key: String, value: AnyRef) = {
    val updatedConfig = Kamon.config()
      .withValue(s"kamon.akka-http.$key", ConfigValueFactory.fromAnyRef(value))
    Kamon.reconfigure(updatedConfig)
  }

}
