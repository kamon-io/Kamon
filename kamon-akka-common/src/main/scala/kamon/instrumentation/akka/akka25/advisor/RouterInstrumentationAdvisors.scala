package akka.kamon.instrumentation.akka25.advisor

import akka.actor.{ActorRef, Cell, Props}
import akka.kamon.instrumentation.{RoutedActorRefAccessor, RouterInstrumentationAware, RouterMonitor}
import kanela.agent.libs.net.bytebuddy.asm.Advice.{Argument, OnMethodExit, This}


/**
  * Advisor for akka.routing.RoutedActorCell::constructor
  */
class RoutedActorCellConstructorAdvisor

object RoutedActorCellConstructorAdvisor {
  @OnMethodExit(suppress = classOf[Throwable])
  def onExit(@This cell: Cell): Unit = {
    cell.asInstanceOf[RouterInstrumentationAware].setRouterInstrumentation(RouterMonitor.createRouterInstrumentation(cell))
  }
}

/**
  * Advisor for akka.routing.RoutedActorRef::constructor
  */
class RoutedActorRefConstructorAdvisor

object RoutedActorRefConstructorAdvisor {
  @OnMethodExit(suppress = classOf[Throwable])
  def onExit(@This ref: ActorRef, @Argument(1) routerProps: Props, @Argument(4) routeeProps: Props): Unit = {
    val routedRef = ref.asInstanceOf[RoutedActorRefAccessor]
    routedRef.setRouteeProps(routeeProps)
    routedRef.setRouterProps(routerProps)
  }
}
