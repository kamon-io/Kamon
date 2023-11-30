package kamon.instrumentation.pekko.instrumentations

import org.apache.pekko.actor.WrappedMessage
import org.apache.pekko.dispatch.Envelope
import kamon.instrumentation.pekko.instrumentations.ActorCellInfo
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.implementation.bind.annotation.Argument
import org.slf4j.LoggerFactory

import scala.annotation.static
import scala.util.control.NonFatal

class ActorMonitorInstrumentation extends InstrumentationBuilder {
   /*
     * Changes implementation of extractMessageClass for our ActorMonitor.
     * In Pekko, all typed messages are converted to AdaptMessage,
     * so we're forced to extract the original message type.
     */
    onSubTypesOf("kamon.instrumentation.pekko.instrumentations.ActorMonitor")
      .intercept(method("extractMessageClass"), classOf[MessageClassAdvice])
}

class MessageClassAdvice
object MessageClassAdvice {
  private val logger = LoggerFactory.getLogger(classOf[MessageClassAdvice])

  @static def extractMessageClass(@Argument(0) envelope: Any): String = {
    val e = envelope.asInstanceOf[Envelope]
    try {
      e.message match {
        case message: WrappedMessage => ActorCellInfo.simpleClassName(message.message.getClass)
        case _ => ActorCellInfo.simpleClassName(e.message.getClass)
      }
    } catch {
      // NoClassDefFound is thrown in early versions of akka 2.6
      // so we can safely fallback to the original method
      case _: NoClassDefFoundError =>
        ActorCellInfo.simpleClassName(e.message.getClass)
      case NonFatal(ex) =>
        logger.info(s"Expected NoClassDefFoundError, got: ${ex}")
        ActorCellInfo.simpleClassName(e.message.getClass)
    }
  }
}
