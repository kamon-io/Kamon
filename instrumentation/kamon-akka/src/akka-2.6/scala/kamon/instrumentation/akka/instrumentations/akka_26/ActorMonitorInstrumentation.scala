package kamon.instrumentation.akka.instrumentations.akka_26

import akka.actor.WrappedMessage
import akka.dispatch.Envelope
import kamon.instrumentation.akka.instrumentations.{ActorCellInfo, VersionFiltering}
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.implementation.bind.annotation.Argument

class ActorMonitorInstrumentation extends InstrumentationBuilder with VersionFiltering {

  onAkka("2.6") {
    /*
     * Changes implementation of extractMessageClass for our ActorMonitor.
     * In akka 2.6, all typed messages are converted to AdaptMessage,
     * so we're forced to extract the original message type.
     */
    onSubTypesOf("kamon.instrumentation.akka.instrumentations.ActorMonitor")
      .intercept(method("extractMessageClass"), MessageClassAdvice)
  }
}

object MessageClassAdvice {
  def extractMessageClass(@Argument(0) envelope: Envelope): String = {
    envelope.message match {
      case message: WrappedMessage => ActorCellInfo.simpleClassName(message.message.getClass)
      case _ => ActorCellInfo.simpleClassName(envelope.message.getClass)
    }
  }
}
