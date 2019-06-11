package kamon.instrumentation.akka.common;

import akka.actor.ActorRef;
import akka.actor.InternalActorRef;

/**
 *  This class exposes access to several private[akka] members that wouldn't be visible from the Scala codebase.
 */
public class AkkaPrivateAccess {


  public static boolean isInternalAndActiveActorRef(ActorRef target) {
    return target != null && target instanceof InternalActorRef && !((InternalActorRef) target).isTerminated();
  }
}