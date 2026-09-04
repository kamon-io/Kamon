/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon.instrumentation.finagle.client

import com.twitter.util.Time
import kamon.trace.Span

import java.net.InetSocketAddress

/**
 * Unique tags and value constants added to Kamon Finagle spans.
 */
private[finagle] object Tags {

  object Keys {
    val ResourceName = "resource.name"
    val PeerPort = "peer.port"
    val PeerHostIPV4 = "peer.ipv4"
    val PeerHostname = "peer.hostname"
  }

  def mark(span: Span, event: String, time: Time): Unit = span.mark(event, time.toInstant)

  def fail(span: Span, event: String, msg: String, time: Time): Unit = {
    mark(span, event, time)
    span.fail(msg)
  }

  def setPeer(span: Span, addr: InetSocketAddress): Unit = {
    span.tag(Tags.Keys.PeerPort, addr.getPort)
    span.tag(Tags.Keys.PeerHostIPV4, addr.getAddress.getHostAddress)
    span.tag(Tags.Keys.PeerHostname, addr.getHostString)
  }
}
