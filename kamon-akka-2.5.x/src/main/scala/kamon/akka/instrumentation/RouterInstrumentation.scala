package akka.kamon.instrumentation

import akka.actor.Props


trait RouterInstrumentationAware {
  def routerInstrumentation: RouterMonitor
  def setRouterInstrumentation(ai: RouterMonitor): Unit
}

object RouterInstrumentationAware {
  def apply(): RouterInstrumentationAware = new RouterInstrumentationAware {
    private var _ri: RouterMonitor = _

    def setRouterInstrumentation(ai: RouterMonitor): Unit = _ri = ai
    def routerInstrumentation: RouterMonitor = _ri
  }
}

trait RoutedActorRefAccessor {
  def routeeProps: Props
  def routerProps: Props
  def setRouteeProps(props: Props): Unit
  def setRouterProps(props: Props): Unit
}

object RoutedActorRefAccessor {
  def apply(): RoutedActorRefAccessor = new RoutedActorRefAccessor {
    private var _routeeProps: Props = _
    private var _routerProps: Props = _

    override def routeeProps: Props = _routeeProps
    override def setRouteeProps(props: Props): Unit = _routeeProps = props

    override def routerProps: Props = _routerProps
    override def setRouterProps(props: Props): Unit = _routerProps = props
  }
}
