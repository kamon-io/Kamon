package kamon.instrumentation.akka.instrumentations

import akka.actor.{ActorRef, ActorSystem, Props}
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice._

import scala.annotation.static

class RouterInstrumentation extends InstrumentationBuilder {

  /**
    * Provides the router metrics tracking implementation.
    */
  onType("akka.routing.RoutedActorCell")
    .mixin(classOf[HasRouterMonitor.Mixin])
    .advise(isConstructor, RoutedActorCellConstructorAdvice)
    .advise(method("sendMessage").and(takesArguments(1)), SendMessageAdvice)
    .advise(method("sendMessage").and(takesArguments(1)), SendMessageOnRouterAdvice)

  /**
    * Captures the router and routee Props so that we can properly apply tags to the router metrics.
    */
  onType("akka.routing.RoutedActorRef")
    .mixin(classOf[HasRouterProps.Mixin])
    .advise(isConstructor, RoutedActorRefConstructorAdvice)
}


/**
  * Helps with capturing the Props for both the router and the routees.
  */
trait HasRouterProps {
  def routeeProps: Props
  def routerProps: Props
  def setRouteeProps(props: Props): Unit
  def setRouterProps(props: Props): Unit
}

object HasRouterProps {

  class Mixin(var routeeProps: Props, var routerProps: Props) extends HasRouterProps {

    override def setRouteeProps(props: Props): Unit =
      this.routeeProps = props

    override def setRouterProps(props: Props): Unit =
      this.routerProps = props
  }
}

trait HasRouterMonitor {
  def routerMonitor: RouterMonitor
  def setRouterMonitor(routerMonitor: RouterMonitor): Unit
}

object HasRouterMonitor {

  class Mixin(var routerMonitor: RouterMonitor) extends HasRouterMonitor {

    override def setRouterMonitor(routerMonitor: RouterMonitor): Unit =
      this.routerMonitor = routerMonitor
  }
}

class RoutedActorRefConstructorAdvice
object RoutedActorRefConstructorAdvice {

  @OnMethodExit(suppress = classOf[Throwable])
  @static def exit(@This ref: ActorRef, @Argument(1) routerProps: Props, @Argument(4) routeeProps: Props): Unit = {
    val routedRef = ref.asInstanceOf[HasRouterProps]
    routedRef.setRouteeProps(routeeProps)
    routedRef.setRouterProps(routerProps)
  }
}

class RoutedActorCellConstructorAdvice
object RoutedActorCellConstructorAdvice {

  @OnMethodExit(suppress = classOf[Throwable])
  @static def exit(@This cell: Any, @Argument(0) system: ActorSystem, @Argument(1) ref: ActorRef, @Argument(5) parent: ActorRef): Unit = {
    cell.asInstanceOf[HasRouterMonitor].setRouterMonitor(RouterMonitor.from(cell, ref, parent, system))
  }
}

class SendMessageOnRouterAdvice
object SendMessageOnRouterAdvice {

  def routerInstrumentation(cell: Any): RouterMonitor =
    cell.asInstanceOf[HasRouterMonitor].routerMonitor

  @OnMethodEnter(suppress = classOf[Throwable])
  @static def onEnter(@This cell: Any): Long =
    routerInstrumentation(cell).processMessageStart()

  @OnMethodExit(suppress = classOf[Throwable])
  @static def onExit(@This cell: Any, @Enter timestampBeforeProcessing: Long): Unit =
    routerInstrumentation(cell).processMessageEnd(timestampBeforeProcessing)
}

