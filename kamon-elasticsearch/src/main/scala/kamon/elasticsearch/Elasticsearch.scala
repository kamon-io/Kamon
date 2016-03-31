/* =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License") you may not use this file
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

package kamon.elasticsearch

import org.elasticsearch.action.ActionRequest

import akka.actor.ReflectiveDynamicAccess
import kamon.Kamon
import kamon.util.ConfigTools.Syntax

object ElasticsearchExtension {
  val SegmentLibraryName = "elasticsearch"

  private val config = Kamon.config.getConfig("kamon.elasticsearch")

  private val dynamic = new ReflectiveDynamicAccess(getClass.getClassLoader)

  private val nameGeneratorFQN = config.getString("name-generator")
  private val nameGenerator: ElasticsearchNameGenerator = dynamic.createInstanceFor[ElasticsearchNameGenerator](nameGeneratorFQN, Nil).get

  private val slowQueryProcessorClass = config.getString("slow-query-processor")
  private val slowQueryProcessor: SlowRequestProcessor = dynamic.createInstanceFor[SlowRequestProcessor](slowQueryProcessorClass, Nil).get

  private val elasticsearchErrorProcessorClass = config.getString("elasticsearch-error-processor")
  private val elasticsearchErrorProcessor: ElasticsearchErrorProcessor = dynamic.createInstanceFor[ElasticsearchErrorProcessor](elasticsearchErrorProcessorClass, Nil).get

  val slowQueryThreshold = config.getFiniteDuration("slow-query-threshold").toMillis

  def processSlowQuery(request: ActionRequest[_], executionTime: Long) = slowQueryProcessor.process(request, executionTime, slowQueryThreshold)
  def processSqlError(request: ActionRequest[_], ex: Throwable) = elasticsearchErrorProcessor.process(request, ex)
  def generateElasticsearchSegmentName(request: ActionRequest[_]): String = nameGenerator.generateElasticsearchSegmentName(request)
}

trait SlowRequestProcessor {
  def process(request: ActionRequest[_], executionTime: Long, queryThreshold: Long): Unit
}

trait ElasticsearchErrorProcessor {
  def process(request: ActionRequest[_], ex: Throwable): Unit
}

trait ElasticsearchNameGenerator {
  def generateElasticsearchSegmentName(request: ActionRequest[_]): String
}

class DefaultElasticsearchNameGenerator extends ElasticsearchNameGenerator {
  def generateElasticsearchSegmentName(request: ActionRequest[_]): String = s"Elasticsearch[${request.getClass.getSimpleName}]"
}

class DefaultElasticsearchErrorProcessor extends ElasticsearchErrorProcessor {

  import org.slf4j.LoggerFactory

  val log = LoggerFactory.getLogger(classOf[DefaultElasticsearchErrorProcessor])

  override def process(request: ActionRequest[_], cause: Throwable): Unit = {
    log.error(s"the request [$request] failed with exception [${cause.getMessage}]")
  }
}

class DefaultSlowRequestProcessor extends SlowRequestProcessor {
  import org.slf4j.LoggerFactory

  val log = LoggerFactory.getLogger(classOf[DefaultSlowRequestProcessor])

  override def process(request: ActionRequest[_], executionTimeInMillis: Long, queryThresholdInMillis: Long): Unit = {
    log.warn(s"The request [$request] took $executionTimeInMillis ms and the slow query threshold is $queryThresholdInMillis ms")
  }
}
