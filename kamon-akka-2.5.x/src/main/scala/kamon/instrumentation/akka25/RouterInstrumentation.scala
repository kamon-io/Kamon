package kamon.instrumentation.akka25

import akka.kamon.instrumentation.akka25.advisor.{RoutedActorCellConstructorAdvisor, RoutedActorRefConstructorAdvisor, SendMessageMethodAdvisor, SendMessageMethodAdvisorForRouter}
import kamon.instrumentation.akka25.mixin.{RoutedActorCellInstrumentationMixin, RoutedActorRefInstrumentationMixin}
import kanela.agent.api.instrumentation.InstrumentationBuilder

class RouterInstrumentation extends InstrumentationBuilder {

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
  onType("akka.routing.RoutedActorCell")
    .mixin(classOf[RoutedActorCellInstrumentationMixin])
    .advise(isConstructor, classOf[RoutedActorCellConstructorAdvisor])
    .advise(method("sendMessage").and(takesArguments(1)), classOf[SendMessageMethodAdvisor])
    .advise(method("sendMessage").and(takesArguments(1)), classOf[SendMessageMethodAdvisorForRouter])

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
  onType("akka.routing.RoutedActorRef")
    .mixin(classOf[RoutedActorRefInstrumentationMixin])
    .advise(isConstructor, classOf[RoutedActorRefConstructorAdvisor])
}

