package kamon.jdbc

import java.util.concurrent.TimeUnit.{ MILLISECONDS ⇒ milliseconds }

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import kamon.Kamon

object Jdbc extends ExtensionId[JdbcExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = Jdbc
  override def createExtension(system: ExtendedActorSystem): JdbcExtension = new JdbcExtension(system)
}

class JdbcExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  private val config = system.settings.config.getConfig("kamon.jdbc")
  private val slowQueryProcessorClass = config.getString("slow-query-processor")
  private val slowQueryProcessor: SlowQueryProcessor = system.dynamicAccess.createInstanceFor[SlowQueryProcessor](slowQueryProcessorClass, Nil).get

  val slowQueryThreshold = config.getDuration("slow-query-threshold", milliseconds)

  def processSlowQuery(sql: String, executionTime: Long) = slowQueryProcessor.process(sql, executionTime, slowQueryThreshold)
}

trait SlowQueryProcessor {
  def process(sql: String, executionTime: Long, queryThreshold: Long): Unit
}

class DefaultSlowQueryProcessor extends SlowQueryProcessor {
  import org.slf4j.LoggerFactory

  val log = LoggerFactory.getLogger("slow-query-processor")

  override def process(sql: String, executionTime: Long, queryThreshold: Long): Unit = {
    log.warn(s"The query: $sql took ${executionTime}ms and the slow query threshold is ${queryThreshold}ms")
  }
}
