/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon.jaeger

import com.typesafe.config.Config
import kamon.module
import kamon.module.{ModuleFactory, SpanReporter}
import kamon.trace.Span
import org.slf4j.LoggerFactory

class JaegerReporterFactory extends ModuleFactory {
  override def create(settings: ModuleFactory.Settings): module.Module = {
    new JaegerReporter(JaegerClient(settings.config))
  }
}

class JaegerReporter(@volatile private var jaegerClient: JaegerClient) extends SpanReporter {

  private val logger = LoggerFactory.getLogger(classOf[JaegerReporter])

  logger.info("Started the Kamon Jaeger reporter")

  override def reconfigure(newConfig: Config): Unit = {
    jaegerClient = JaegerClient(newConfig)
  }

  override def stop(): Unit = {
    logger.info("Stopped the Kamon Jaeger reporter")
  }

  override def reportSpans(spans: Seq[Span.Finished]): Unit = {
    jaegerClient.sendSpans(spans)
  }
}
