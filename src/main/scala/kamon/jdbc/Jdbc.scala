/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config
import kamon.{Kamon, OnReconfigureHook}
import kamon.util.DynamicAccess
import org.slf4j.LoggerFactory

object Jdbc {
  private val logger = LoggerFactory.getLogger(Jdbc.getClass)
  @volatile private var slowQueryThresholdMicroseconds: Long = 2000000
  @volatile private var slowQueryProcessor: SlowQueryProcessor = new SlowQueryProcessor.Default
  @volatile private var sqlErrorProcessor: SqlErrorProcessor = new SqlErrorProcessor.Default

  loadConfiguration(Kamon.config())

  Kamon.onReconfigure(new OnReconfigureHook {
    override def onReconfigure(newConfig: Config): Unit =
      Jdbc.loadConfiguration(newConfig)
  })



  private def loadConfiguration(config: Config): Unit = {
    try {
      val jdbcConfig = config.getConfig("kamon.jdbc")
      val dynamic = new DynamicAccess(getClass.getClassLoader)

      val slowQueryProcessorFQCN = jdbcConfig.getString("slow-query-processor")
      slowQueryProcessor = dynamic.createInstanceFor[SlowQueryProcessor](slowQueryProcessorFQCN, Nil).get
      slowQueryThresholdMicroseconds = jdbcConfig.getDuration("slow-query-threshold", TimeUnit.MICROSECONDS)

      val sqlErrorProcessorFQCN = jdbcConfig.getString("sql-error-processor")
      sqlErrorProcessor = dynamic.createInstanceFor[SqlErrorProcessor](sqlErrorProcessorFQCN, Nil).get

    } catch {
      case t: Throwable => logger.error("The kamon-jdbc module failed to load configuration", t)
    }
  }


  def onStatementFinish(statement: String, elapsedTimeMicroseconds: Long): Unit = {
    if(elapsedTimeMicroseconds > slowQueryThresholdMicroseconds)
      slowQueryProcessor.process(statement, elapsedTimeMicroseconds, slowQueryThresholdMicroseconds)
  }

  def onStatementError(statement: String, error: Throwable): Unit = {
    sqlErrorProcessor.process(statement, error)
  }

  /**
    * Callback for notifications of statements taking longer than kamon.jdbc.slow-query-threshold microseconds to
    * execute.
    *
    */
  trait SlowQueryProcessor {
    def process(statement: String, elapsedTimeMicroseconds: Long, slowThresholdMicroseconds: Long): Unit
  }

  object SlowQueryProcessor {
    final class Default extends SlowQueryProcessor {
      private val log = LoggerFactory.getLogger(classOf[SlowQueryProcessor.Default])

      override def process(statement: String, elapsedTimeMicroseconds: Long, slowThresholdMicroseconds: Long): Unit =
        log.warn("Query execution exceeded the [{}] microseconds threshold and lasted [{}] microseconds. The query was: [{}]",
          slowThresholdMicroseconds.toString, elapsedTimeMicroseconds.toString, statement)
    }
  }

  /**
    * Callback for notifications on errors thrown while executing statements.
    *
    */
  trait SqlErrorProcessor {
    def process(sql: String, ex: Throwable): Unit
  }

  object SqlErrorProcessor {
    final class Default extends SqlErrorProcessor {
      private val log = LoggerFactory.getLogger(classOf[SqlErrorProcessor.Default])

      override def process(sql: String, ex: Throwable): Unit =
        log.error("State [{}] failed to execute", ex)
    }
  }
}