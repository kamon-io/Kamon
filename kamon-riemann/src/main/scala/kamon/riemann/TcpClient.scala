/*
 * =========================================================================================
 * Copyright © 2013-2016 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.riemann

import java.net.InetSocketAddress

import akka.actor._
import akka.io.Tcp
import akka.io.Tcp._
import akka.util.ByteString
import com.aphyr.riemann.Proto.Msg
import com.aphyr.riemann.Proto.Event
import kamon.riemann.TcpClient.Send
import scala.concurrent.duration._

class TcpClient(tcpManager: ActorRef, address: InetSocketAddress) extends Actor with ActorLogging with Stash {

  private case object Ack extends Tcp.Event

  private val connectionTimeout = Some(3 seconds)

  tcpManager ! Connect(address, timeout = connectionTimeout)

  private def writeInt(v: Int): Array[Byte] = {
    Array(((v >>> 24) & 0xFF).toByte, ((v >>> 16) & 0xFF).toByte, ((v >>> 8) & 0xFF).toByte, ((v >>> 0) & 0xFF).toByte)
  }

  private def writePartially(remaining: Iterable[Msg], connection: ActorRef): Unit = {
    if (remaining.nonEmpty) {
      val msg = remaining.head.toByteArray
      val payload = ByteString(writeInt(msg.length)).concat(ByteString(msg))
      connection ! Write(payload, Ack)
      context become ({
        case Ack ⇒ {
          context.unbecome()
          writePartially(remaining.tail, connection)
        }
        case Received(resp) ⇒ // ignore
        case CommandFailed(Write(_, Ack)) ⇒ {
          // TODO: retry mechanism
          log.error("Couldn't write an event to Riemann over TCP")
          context.unbecome()
          writePartially(remaining.tail, connection)
        }
      }, discardOld = false)
    } else {
      connection ! ConfirmedClose
    }
  }

  private def shuttingDown: Receive = {
    case Send(e) ⇒ // ignore
  }

  private def connecting(remainingConnectRetries: Int, backoff: Int): Receive = {
    case CommandFailed(_: Connect) ⇒ {
      if (remainingConnectRetries > 0) {
        context.system.scheduler.scheduleOnce((Math.pow(2, backoff) * 200).millisecond) {
          tcpManager ! Connect(address, timeout = connectionTimeout)
        }(context.dispatcher)
        context.become(connecting(remainingConnectRetries - 1, backoff + 1), discardOld = true)
        log.warning(s"Problem with TCP connection to Riemann - reconnecting")
      } else {
        // unstash all messages before shutting down actor to avoid '...was not delivered. [1] dead letters encountered...'
        context.become(shuttingDown, discardOld = true)
        unstashAll()
        log.error("Couldn't establish TCP connection to Riemann")
        self ! PoisonPill
      }
    }

    case Send(events) ⇒ stash()

    case Connected(remote, local) ⇒ {
      val connection = sender()
      connection ! Register(self)
      unstashAll()
      context.become(connected(connection), discardOld = true)
    }
  }

  private def connected(connection: ActorRef): Receive = {
    case Received(resp) ⇒ // ignore
    case Send(events) ⇒ {
      val buffer = events.map(Msg.newBuilder().addEvents(_).build())
      writePartially(buffer, connection)
    }
    case _: ConnectionClosed ⇒ context.stop(self)
  }

  override def receive: Receive = connecting(3, 1)
}

object TcpClient {

  case class Send(events: Iterable[Event])

  def props(tcpManager: ActorRef, addres: InetSocketAddress): Props = {
    Props(new TcpClient(tcpManager, addres))
  }
}
