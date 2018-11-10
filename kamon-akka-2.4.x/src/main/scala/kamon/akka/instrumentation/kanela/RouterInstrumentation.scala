package kamon.akka.instrumentation.kanela

import akka.kamon.instrumentation.kanela.advisor.{RoutedActorCellConstructorAdvisor, RoutedActorRefConstructorAdvisor, SendMessageMethodAdvisor, SendMessageMethodAdvisorForRouter}
import kamon.akka.instrumentation.kanela.mixin.{RoutedActorCellInstrumentationMixin, RoutedActorRefInstrumentationMixin}
import kanela.agent.scala.KanelaInstrumentation

class RouterInstrumentation extends KanelaInstrumentation {

  /**
    * Instrument:
    *
    * akka.routing.RoutedActorCell::constructor
    * akka.routing.RoutedActorCell::sendMessage
    *
    * Mix:
    *
    * akka.routing.RoutedActorCell with kamon.akka.instrumentation.mixin.RouterInstrumentationAware
    *
    */
  forTargetType("akka.routing.RoutedActorCell") { builder ⇒
    builder
      .withMixin(classOf[RoutedActorCellInstrumentationMixin])
      .withAdvisorFor(Constructor, classOf[RoutedActorCellConstructorAdvisor])
      .withAdvisorFor(method("sendMessage").and(takesArguments(1)), classOf[SendMessageMethodAdvisor])
      .withAdvisorFor(method("sendMessage").and(takesArguments(1)), classOf[SendMessageMethodAdvisorForRouter])
      .build()
  }

  /**
    * Instrument:
    *
    * akka.routing.RoutedActorRef::constructor
    * akka.routing.RoutedActorRef::sendMessage
    *
    * Mix:
    *
    * akka.routing.RoutedActorRef with kamon.akka.instrumentation.mixin.RoutedActorRefAccessor
    *
    */
  forTargetType("akka.routing.RoutedActorRef") { builder ⇒
    builder
      .withMixin(classOf[RoutedActorRefInstrumentationMixin])
      .withAdvisorFor(Constructor, classOf[RoutedActorRefConstructorAdvisor])
      .build()
  }
}
