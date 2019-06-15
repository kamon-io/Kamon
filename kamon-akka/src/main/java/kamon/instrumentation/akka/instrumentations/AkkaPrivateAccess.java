package kamon.instrumentation.akka.instrumentations;

import akka.actor.*;
import akka.dispatch.sysmsg.SystemMessage;
import akka.routing.RoutedActorCell;
import akka.routing.RoutedActorRef;
import scala.Option;

/**
 *  This class exposes access to several private[akka] members that wouldn't be visible from the Scala codebase.
 */
public class AkkaPrivateAccess {

  public static boolean isSystemMessage(Object message) {
    return message instanceof SystemMessage;
  }

  public static boolean isInternalAndActiveActorRef(ActorRef target) {
    return target != null && target instanceof InternalActorRef && !((InternalActorRef) target).isTerminated();
  }

  public static boolean isRoutedActorRef(ActorRef target) {
    return target instanceof RoutedActorRef;
  }

  public static boolean isRoutedActorCell(Object cell) {
    return cell instanceof RoutedActorCell;
  }

  public static boolean isUnstartedActorCell(Object cell) {
    return cell instanceof UnstartedCell;
  }

  public static Class<?> unstartedActorCellClass() {
    return UnstartedCell.class;
  }

  public static long mailboxMessageCount(Object cell) {
    return ((ActorCell) cell).mailbox().numberOfMessages();
  }

  public static Option<Props> cellProps(Object cell) {
    if(cell != null && cell instanceof Cell)
      return Option.apply(((Cell) cell).props());
    else
      return Option.empty();
  }

  public static Option<Deploy> lookupDeploy(ActorPath path, ActorSystem system) {
    final Deployer deployer = new Deployer(system.settings(), ((ExtendedActorSystem) system).dynamicAccess());
    return deployer.lookup(path.$div("$a"));
  }
}