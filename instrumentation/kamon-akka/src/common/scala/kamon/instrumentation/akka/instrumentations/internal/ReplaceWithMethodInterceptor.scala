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

package akka.instrumentation

import java.util.concurrent.locks.ReentrantLock

import akka.actor.{Cell, UnstartedCell}
import akka.dispatch.Envelope
import akka.dispatch.sysmsg.{LatestFirstSystemMessageList, SystemMessage, SystemMessageList}
import kamon.Kamon
import kamon.instrumentation.context.HasContext
import kanela.agent.libs.net.bytebuddy.implementation.bind.annotation.{Argument, RuntimeType, This}

object ReplaceWithMethodInterceptor {

  @RuntimeType
  def aroundReplaceWithInRepointableActorRef(@This unStartedCell: Object, @Argument(0) cell: Object): Unit = {
    import kamon.instrumentation.akka.instrumentations.ActorInstrumentation._

    val queue = unstartedCellQueueField.get(unStartedCell).asInstanceOf[java.util.LinkedList[_]]
    val lock = unstartedCellLockField.get(unStartedCell).asInstanceOf[ReentrantLock]
    val sysQueueHead = systemMsgQueueField.get(unStartedCell).asInstanceOf[SystemMessage]

    def locked[T](body: => T): T = {
      lock.lock()
      try body finally lock.unlock()
    }

    var sysQueue =  new LatestFirstSystemMessageList(sysQueueHead)

    locked {
      try {
        def drainSysmsgQueue(): Unit = {
          // using while in case a sys msg enqueues another sys msg
          while (sysQueue.nonEmpty) {
            var sysQ = sysQueue.reverse
            sysQueue = SystemMessageList.LNil
            while (sysQ.nonEmpty) {
              val msg = sysQ.head
              sysQ = sysQ.tail
              msg.unlink()
              cell.asInstanceOf[Cell].sendSystemMessage(msg)
            }
          }
        }

        drainSysmsgQueue()

        while (!queue.isEmpty) {
          queue.poll() match {
            case e: HasContext =>
              Kamon.runWithContext(e.context) {
                cell.asInstanceOf[Cell].sendMessage(e.asInstanceOf[Envelope])
              }
          }

          drainSysmsgQueue()
        }
      } finally {
        unStartedCell.asInstanceOf[UnstartedCell].self.swapCell(cell.asInstanceOf[Cell])
      }
    }
  }
}
