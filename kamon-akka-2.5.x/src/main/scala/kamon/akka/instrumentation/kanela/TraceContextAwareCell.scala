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

package akka.kamon.instrumentation

import akka.actor.dungeon.ChildrenContainer
import akka.actor.{ActorRef, ActorSystem, ActorSystemImpl, Cell, ChildStats, InternalActorRef, Props}
import akka.dispatch.Envelope
import akka.dispatch.sysmsg.SystemMessage
import kamon.Kamon

  /**
    * Wrap a akka.actor.Cell in order to propagate the current TraceContext when calling sendMessage method
    */
class TraceContextAwareCell(underlying: Cell) extends Cell {
    def self: ActorRef = underlying.self
    def isTerminated: Boolean = underlying.isTerminated
    def getSingleChild(name: String): InternalActorRef = underlying.getSingleChild(name)
    def stop(): Unit = underlying.stop()
    def numberOfMessages: Int = underlying.numberOfMessages
    def isLocal: Boolean = underlying.isLocal
    def props: Props = underlying.props
    def getChildByName(name: String): Option[ChildStats] = underlying.getChildByName(name)
    def restart(cause: Throwable): Unit = underlying.restart(cause)
    def suspend(): Unit = underlying.suspend()
    def hasMessages: Boolean = underlying.hasMessages
    def systemImpl: ActorSystemImpl = underlying.systemImpl
    def resume(causedByFailure: Throwable): Unit = underlying.resume(causedByFailure)
    def start() = this
    def childrenRefs: ChildrenContainer = underlying.childrenRefs
    def parent: InternalActorRef = underlying.parent
    def system: ActorSystem = underlying.system
    def sendSystemMessage(msg: SystemMessage): Unit = {
      underlying.sendSystemMessage(msg)
    }

    def sendMessage(msg: Envelope): Unit = {
      val envelopeContext = msg.asInstanceOf[InstrumentedEnvelope].timestampedContext()

      if (envelopeContext != null) {
        Kamon.withContext(envelopeContext.context) {
          underlying.sendMessage(msg)
        }
      } else {
        underlying.sendMessage(msg)
      }
    }
}
