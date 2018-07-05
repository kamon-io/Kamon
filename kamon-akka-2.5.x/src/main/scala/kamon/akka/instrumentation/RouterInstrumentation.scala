package akka.kamon.instrumentation

import akka.actor.{ Props, ActorRef, ActorSystem, Cell }
import akka.dispatch.{ Envelope, MessageDispatcher }
import akka.routing.RoutedActorCell
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._

@Aspect
class RoutedActorCellInstrumentation {

  def routerInstrumentation(cell: Cell): RouterMonitor =
    cell.asInstanceOf[RouterInstrumentationAware].routerInstrumentation

  @Pointcut("execution(akka.routing.RoutedActorCell.new(..)) && this(cell) && args(system, ref, props, dispatcher, routeeProps, supervisor)")
  def routedActorCellCreation(cell: RoutedActorCell, system: ActorSystem, ref: ActorRef, props: Props, dispatcher: MessageDispatcher, routeeProps: Props, supervisor: ActorRef): Unit = {}

  @After("routedActorCellCreation(cell, system, ref, props, dispatcher, routeeProps, supervisor)")
  def afterRoutedActorCellCreation(cell: RoutedActorCell, system: ActorSystem, ref: ActorRef, props: Props, dispatcher: MessageDispatcher, routeeProps: Props, supervisor: ActorRef): Unit = {
    cell.asInstanceOf[RouterInstrumentationAware].setRouterInstrumentation(
      RouterMonitor.createRouterInstrumentation(cell))
  }

  @Pointcut("execution(* akka.routing.RoutedActorCell.sendMessage(*)) && this(cell) && args(envelope)")
  def sendMessageInRouterActorCell(cell: RoutedActorCell, envelope: Envelope) = {}

  @Around("sendMessageInRouterActorCell(cell, envelope)")
  def aroundSendMessageInRouterActorCell(pjp: ProceedingJoinPoint, cell: RoutedActorCell, envelope: Envelope): Any = {
    routerInstrumentation(cell).processMessage(pjp)
  }

  @Pointcut("execution(akka.routing.RoutedActorRef.new(..)) && this(ref) && args(*, routerProps, *, *, routeeProps, *, *)")
  def routedActorRefCreation(ref: ActorRef, routerProps: Props, routeeProps: Props): Unit = {}

  @Before("routedActorRefCreation(ref, routerProps, routeeProps)")
  def beforeRoutedActorRefCreation(ref: ActorRef, routerProps: Props, routeeProps: Props): Unit = {
    val routedRef = ref.asInstanceOf[RoutedActorRefAccessor]
    routedRef.setRouteeProps(routeeProps)
    routedRef.setRouterProps(routerProps)
  }
}

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

@Aspect
class MetricsIntoRouterCellsMixin {

  @DeclareMixin("akka.routing.RoutedActorCell")
  def mixinActorCellMetricsToRoutedActorCell: RouterInstrumentationAware = RouterInstrumentationAware()

  @DeclareMixin("akka.routing.RoutedActorRef")
  def mixinRoutedActorRefAccessorToRoutedActorRef: RoutedActorRefAccessor = RoutedActorRefAccessor()

}