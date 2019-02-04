/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import kamon.metric.PeriodSnapshot
import kamon.trace.Span

object Kamon extends ClassLoading
  with Configuration
  with Utilities
  with Metrics
  with Tracing
  with ModuleLoading
  with ContextPropagation
  with ContextStorage
  with StatusPage {


  @volatile private var _environment = Environment.fromConfig(config())

  def environment: Environment =
    _environment

  onReconfigure(newConfig => {
    _environment = Environment.fromConfig(config)
  })
}


object QuickTest extends App {
  val manualConfig =
    """
      |kamon.modules {
      |  kamon-zipkin {
      |    enabled = false
      |    description = "Module that sends data to particular places"
      |    kind = metric
      |    class = kamon.MyCustomMetricDude
      |  }
      |}
      |
      |kamon.environment.tags {
      |  one = test
      |}
    """.stripMargin

  val newConfig = ConfigFactory.parseString(manualConfig).withFallback(Kamon.config())
  Kamon.reconfigure(newConfig)



  Kamon.loadModules()
  Kamon.registerModule("my-module", new kamon.module.MetricReporter {
    override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {}
    override def start(): Unit = {}
    override def stop(): Unit = {}
    override def reconfigure(newConfig: Config): Unit = {}
  })

  Kamon.registerModule("my-module-for-spans", new kamon.module.SpanReporter {
    override def reportSpans(spans: Seq[Span.FinishedSpan]): Unit = {}
    override def start(): Unit = {}
    override def stop(): Unit = {}
    override def reconfigure(newConfig: Config): Unit = {}
  })


  Kamon.histogram("test").refine("actor_class" -> "com.kamon.something.MyActor", "system" -> "HRMS").record(10)
  Kamon.rangeSampler("test-rs").refine("actor_class" -> "com.kamon.something.MyActor", "system" -> "HRMS").increment(34)
  Kamon.counter("test-counter").refine("tagcito" -> "value").increment(42)

  //println("JSON CONFIG: " + Kamon.config().root().render(ConfigRenderOptions.concise().setFormatted(true).setJson(true)))


  Thread.sleep(100000000)


}

class MyCustomMetricDude extends kamon.module.MetricReporter {
  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {}
  override def start(): Unit = {}
  override def stop(): Unit = {}
  override def reconfigure(newConfig: Config): Unit = {}
}
