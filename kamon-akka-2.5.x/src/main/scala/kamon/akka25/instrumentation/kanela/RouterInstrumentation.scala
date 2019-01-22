package kamon.akka25.instrumentation.kanela

import kanela.agent.scala.KanelaInstrumentation
import kamon.akka25.instrumentation.kanela.AkkaVersionedFilter._
import akka.kamon.akka25.instrumentation.kanela.advisor.{RoutedActorCellConstructorAdvisor, RoutedActorRefConstructorAdvisor, SendMessageMethodAdvisor, SendMessageMethodAdvisorForRouter}
import kamon.akka25.instrumentation.kanela.mixin.{RoutedActorCellInstrumentationMixin, RoutedActorRefInstrumentationMixin}

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
    filterAkkaVersion(builder)
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
    filterAkkaVersion(builder)
      .withMixin(classOf[RoutedActorRefInstrumentationMixin])
      .withAdvisorFor(Constructor, classOf[RoutedActorRefConstructorAdvisor])
      .build()
  }
}
