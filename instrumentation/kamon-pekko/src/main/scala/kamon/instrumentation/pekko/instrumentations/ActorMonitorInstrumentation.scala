package kamon.instrumentation.pekko.instrumentations

import org.apache.pekko.actor.WrappedMessage
import org.apache.pekko.dispatch.Envelope
import kamon.instrumentation.pekko.instrumentations.ActorCellInfo
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.implementation.bind.annotation.Argument
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal

class ActorMonitorInstrumentation extends InstrumentationBuilder {
   /*
     * Changes implementation of extractMessageClass for our ActorMonitor.
     * In akka 2.6, all typed messages are converted to AdaptMessage,
     * so we're forced to extract the original message type.
     */
    onSubTypesOf("kamon.instrumentation.pekko.instrumentations.ActorMonitor")
      .intercept(method("extractMessageClass"), MessageClassAdvice)
}

class MessageClassAdvice
object MessageClassAdvice {
  private val logger = LoggerFactory.getLogger(classOf[MessageClassAdvice])

  def extractMessageClass(@Argument(0) envelope: Envelope): String = {
    try {
      envelope.message match {
        case message: WrappedMessage => ActorCellInfo.simpleClassName(message.message.getClass)
        case _ => ActorCellInfo.simpleClassName(envelope.message.getClass)
      }
    } catch {
      // NoClassDefFound is thrown in early versions of akka 2.6
      // so we can safely fallback to the original method
      case _: NoClassDefFoundError =>
        ActorCellInfo.simpleClassName(envelope.message.getClass)
      case NonFatal(e) =>
        logger.info(s"Expected NoClassDefFoundError, got: ${e}")
        ActorCellInfo.simpleClassName(envelope.message.getClass)
    }
  }
}
