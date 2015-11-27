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

package kamon.jdbc

import kamon.util.ConfigTools.Syntax

import akka.actor._
import kamon.Kamon
import kamon.util.logger.LazyLogger

object JdbcExtension {
  val log = LazyLogger("kamon.jdbc.JdbcExtension")

  val SegmentLibraryName = "jdbc"

  private val config = Kamon.config.getConfig("kamon.jdbc")
  private val dynamic = new ReflectiveDynamicAccess(getClass.getClassLoader)

  private val nameGeneratorFQN = config.getString("name-generator")
  private val nameGenerator: JdbcNameGenerator = dynamic.createInstanceFor[JdbcNameGenerator](nameGeneratorFQN, Nil).get

  private val slowQueryProcessorClass = config.getString("slow-query-processor")
  private val slowQueryProcessor: SlowQueryProcessor = dynamic.createInstanceFor[SlowQueryProcessor](slowQueryProcessorClass, Nil).get

  private val sqlErrorProcessorClass = config.getString("sql-error-processor")
  private val sqlErrorProcessor: SqlErrorProcessor = dynamic.createInstanceFor[SqlErrorProcessor](sqlErrorProcessorClass, Nil).get

  val slowQueryThreshold = config.getFiniteDuration("slow-query-threshold").toMillis

  def processSlowQuery(sql: String, executionTime: Long) = slowQueryProcessor.process(sql, executionTime, slowQueryThreshold)
  def processSqlError(sql: String, ex: Throwable) = sqlErrorProcessor.process(sql, ex)
  def generateJdbcSegmentName(statement: String): String = nameGenerator.generateJdbcSegmentName(statement)
}

trait SlowQueryProcessor {
  def process(sql: String, executionTime: Long, queryThreshold: Long): Unit
}

trait SqlErrorProcessor {
  def process(sql: String, ex: Throwable): Unit
}

trait JdbcNameGenerator {
  def generateJdbcSegmentName(statement: String): String
}

class DefaultJdbcNameGenerator extends JdbcNameGenerator {
  def generateJdbcSegmentName(statement: String): String = s"Jdbc[$statement]"
}

class DefaultSqlErrorProcessor extends SqlErrorProcessor {
  val log = LazyLogger(classOf[DefaultSqlErrorProcessor])

  override def process(sql: String, cause: Throwable): Unit = {
    log.error(s"the query [$sql] failed with exception [${cause.getMessage}]")
  }
}

class DefaultSlowQueryProcessor extends SlowQueryProcessor {
  val log = LazyLogger(classOf[DefaultSlowQueryProcessor])

  override def process(sql: String, executionTimeInMillis: Long, queryThresholdInMillis: Long): Unit = {
    log.warn(s"The query [$sql] took $executionTimeInMillis ms and the slow query threshold is $queryThresholdInMillis ms")
  }
}
