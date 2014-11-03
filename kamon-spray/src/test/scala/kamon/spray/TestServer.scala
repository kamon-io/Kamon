/*
 * =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
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

package kamon.spray

import akka.testkit.{ TestProbe, TestKitBase }
import spray.can.Http
import akka.actor.ActorRef
import akka.io.IO
import akka.io.Tcp.Bound
import scala.concurrent.duration._

trait TestServer {
  self: TestKitBase ⇒

  def buildClientConnectionAndServer: (ActorRef, TestProbe) = {
    val serverHandler = TestProbe()
    IO(Http).tell(Http.Bind(listener = serverHandler.ref, interface = "127.0.0.1", port = 0), serverHandler.ref)
    val bound = serverHandler.expectMsgType[Bound](10 seconds)
    val client = clientConnection(bound)

    serverHandler.expectMsgType[Http.Connected]
    serverHandler.reply(Http.Register(serverHandler.ref))

    (client, serverHandler)
  }

  private def clientConnection(connectionInfo: Http.Bound): ActorRef = {
    val probe = TestProbe()
    probe.send(IO(Http), Http.Connect(connectionInfo.localAddress.getHostName, connectionInfo.localAddress.getPort))
    probe.expectMsgType[Http.Connected]
    probe.sender
  }

  def buildSHostConnectorAndServer: (ActorRef, TestProbe, Http.Bound) = {
    val serverHandler = TestProbe()
    IO(Http).tell(Http.Bind(listener = serverHandler.ref, interface = "127.0.0.1", port = 0), serverHandler.ref)
    val bound = serverHandler.expectMsgType[Bound](10 seconds)
    val client = httpHostConnector(bound)

    (client, serverHandler, bound)
  }

  private def httpHostConnector(connectionInfo: Http.Bound): ActorRef = {
    val probe = TestProbe()
    probe.send(IO(Http), Http.HostConnectorSetup(connectionInfo.localAddress.getHostName, connectionInfo.localAddress.getPort))
    probe.expectMsgType[Http.HostConnectorInfo].hostConnector
  }

}
