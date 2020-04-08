package kamon.instrumentation.kafka.client

import com.typesafe.config.Config
import kamon.Kamon
import kamon.context.Context
import kamon.instrumentation.context.HasContext
import kamon.trace.Span
import org.apache.kafka.clients.consumer.ConsumerRecord

object KafkaInstrumentation {
  @volatile private var _settings: Settings = readSettings(Kamon.config())
  Kamon.onReconfigure((newConfig: Config) => _settings = readSettings(newConfig))

  def settings: Settings =
    _settings

  private def readSettings(config: Config): Settings = {
    val kafkaConfig = config.getConfig("kamon.instrumentation.kafka.client")

    Settings(
      continueTraceOnConsumer = kafkaConfig.getBoolean("tracing.continue-trace-on-consumer"),
      useDelayedSpans = kafkaConfig.getBoolean("tracing.use-delayed-spans")
    )
  }

  /**
    * Syntactical sugar to extract a Context and Span from a ConsumerRecord instance.
    */
  implicit class Syntax(cr: ConsumerRecord[_, _]) {
    def context: Context =
      cr.asInstanceOf[HasContext].context

    def span: Span =
      cr.asInstanceOf[HasContext].context.get(Span.Key)

    protected[kafka] def setContext(s: Context): Unit =
      cr.asInstanceOf[HasContext].setContext(s)
  }


  def getContext[K, V](consumerRecord: ConsumerRecord[K, V]): Context = {
    consumerRecord.context
  }

  def getSpan[K, V](consumerRecord: ConsumerRecord[K, V]): Span = {
    consumerRecord.span
  }



  object Keys {
    val Null = "NULL"
  }

  case class Settings (
    continueTraceOnConsumer: Boolean,
    useDelayedSpans: Boolean
  )
}
