/*
 * =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package akka.actor.instrumentation

import akka.actor.dungeon.ChildrenContainer

import akka.actor.{ActorRef, ActorSystem, ActorSystemImpl, Cell, ChildStats, InternalActorRef, Props}
import akka.dispatch.Envelope
import akka.dispatch.sysmsg.SystemMessage
import kamon.Kamon
import kamon.instrumentation.context.HasContext

/**
  * Thin wrapper used while exchanging an UnstartedCell for a real ActorCell instance. This wrapper is only
  * meant to be used during the execution of UnstartedCell.replaceWith(...), for the sole purpose of ensuring
  * that all messages that might have been accumulated in the UnstartedCell will get their Context propagated
  * as expected.
  *
  * For reference, we used to have a copy/pasted and modified version of UnstartedCell.replaceWith(...) as part
  * of the instrumentation, but there were tiny bugs related to accessing internal state on the UnstartedCell while
  * running our modified version of the method. These bugs lead to losing System Messages in certain situations,
  * which eventually leads to actors not being shut down. The CellWrapper approach ensures that the internal calls
  * to UnstartedCell.replaceWith#drainSysmsgQueue() are unchanged, while still ensuring that the Kamon Context
  * will be propagated for all queued messages.
  */
class CellWrapper(val underlying: Cell) extends Cell {
  override def sendMessage(msg: Envelope): Unit = try {
    val context = msg.asInstanceOf[HasContext].context
    Kamon.runWithContext(context) {
      underlying.sendMessage(msg)
    }
  }
  catch {
    case _: ClassCastException => underlying.sendMessage(msg)
  }

  override def sendSystemMessage(msg: SystemMessage): Unit =
    underlying.sendSystemMessage(msg)

  // None of these methods below this point will ever be called.

  override def self: ActorRef = underlying.self
  override def system: ActorSystem = underlying.system
  override def systemImpl: ActorSystemImpl = underlying.systemImpl
  override def start(): this.type = underlying.start().asInstanceOf[this.type]
  override def suspend(): Unit = underlying.suspend()
  override def resume(causedByFailure: Throwable): Unit = underlying.resume(causedByFailure)
  override def restart(cause: Throwable): Unit = underlying.restart(cause)
  override def stop(): Unit = underlying.stop()
  override private[akka] def isTerminated = underlying.isTerminated
  override def parent: InternalActorRef = underlying.parent
  override def childrenRefs: ChildrenContainer = underlying.childrenRefs
  override def getChildByName(name: String): Option[ChildStats] = underlying.getChildByName(name)
  override def getSingleChild(name: String): InternalActorRef = underlying.getSingleChild(name)
  override def isLocal: Boolean = underlying.isLocal
  override def hasMessages: Boolean = underlying.hasMessages
  override def numberOfMessages: Int = underlying.numberOfMessages
  override def props: Props = underlying.props
}


