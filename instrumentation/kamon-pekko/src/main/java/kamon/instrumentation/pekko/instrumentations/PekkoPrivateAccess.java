package kamon.instrumentation.pekko.instrumentations;

import org.apache.pekko.actor.*;
import org.apache.pekko.dispatch.Mailbox;
import org.apache.pekko.dispatch.sysmsg.SystemMessage;
import org.apache.pekko.pattern.PromiseActorRef;
import org.apache.pekko.routing.RoutedActorCell;
import org.apache.pekko.routing.RoutedActorRef;
import scala.Option;

/**
 *  This class exposes access to several private[pekko] members that wouldn't be visible from the Scala codebase.
 */
public class PekkoPrivateAccess {

  public static boolean isSystemMessage(Object message) {
    return message instanceof SystemMessage;
  }

  public static boolean isPromiseActorRef(ActorRef ref) {
    return ref instanceof PromiseActorRef;
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

  public static boolean isDeadLettersMailbox(Object cell, Object mailbox) {
    final ActorCell actorCell = (ActorCell) cell;
    return mailbox == actorCell.dispatcher().mailboxes().deadLetterMailbox();
  }

  public static long mailboxMessageCount(Object mailbox) {
    return ((Mailbox) mailbox).numberOfMessages();
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