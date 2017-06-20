/*
 * =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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


package kamon.jaeger;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

/*
 * A thrift transport for sending sending/receiving spans.
 */
public class TUDPTransport extends TTransport implements Closeable {
  public static final int MAX_PACKET_SIZE = 65000;

  public final DatagramSocket socket;
  public byte[] receiveBuf;
  public int receiveOffSet = -1;
  public int receiveLength = 0;
  public ByteBuffer writeBuffer;

  // Create a UDP client for sending data to specific host and port
  public static TUDPTransport NewTUDPClient(String host, int port) {
    TUDPTransport t;
    try {
      t = new TUDPTransport();
      t.socket.connect(new InetSocketAddress(host, port));
    } catch (SocketException e) {
      throw new RuntimeException("TUDPTransport cannot connect: ", e);
    }
    return t;
  }

  // Create a UDP server for receiving data on specific host and port
  public static TUDPTransport NewTUDPServer(String host, int port)
      throws SocketException, UnknownHostException {
    TUDPTransport t = new TUDPTransport();
    t.socket.bind(new InetSocketAddress(host, port));
    return t;
  }

  private TUDPTransport() throws SocketException {
    this.socket = new DatagramSocket(null);
  }

  int getPort() {
    return socket.getLocalPort();
  }

  @Override
  public boolean isOpen() {
    return !this.socket.isClosed();
  }

  // noop as opened in constructor
  @Override
  public void open() throws TTransportException {}

  // close underlying socket
  @Override
  public void close() {
    this.socket.close();
  }

  @Override
  public int getBytesRemainingInBuffer() {
    // This forces thrift 0.9.2 to use its read_all path which works for reading from
    // sockets, since it fetches everything incrementally, rather than having a different
    // implementation based on some fixed buffer size.
    return 0;
  }

  @Override
  public int read(byte[] bytes, int offset, int len) throws TTransportException {
    if (!this.isOpen()) {
      throw new TTransportException(TTransportException.NOT_OPEN);
    }
    if (this.receiveOffSet == -1) {
      this.receiveBuf = new byte[MAX_PACKET_SIZE];
      DatagramPacket dg = new DatagramPacket(this.receiveBuf, MAX_PACKET_SIZE);
      try {
        this.socket.receive(dg);
      } catch (IOException e) {
        throw new TTransportException(
            TTransportException.UNKNOWN, "ERROR from underlying socket", e);
      }
      this.receiveOffSet = 0;
      this.receiveLength = dg.getLength();
    }
    int curDataSize = this.receiveLength - this.receiveOffSet;
    if (curDataSize <= len) {
      System.arraycopy(this.receiveBuf, this.receiveOffSet, bytes, offset, curDataSize);
      this.receiveOffSet = -1;
      return curDataSize;
    } else {
      System.arraycopy(this.receiveBuf, this.receiveOffSet, bytes, offset, len);
      this.receiveOffSet += len;
      return len;
    }
  }

  @Override
  public void write(byte[] bytes, int offset, int len) throws TTransportException {
    if (!this.isOpen()) {
      throw new TTransportException(TTransportException.NOT_OPEN);
    }
    if (this.writeBuffer == null) {
      this.writeBuffer = ByteBuffer.allocate(MAX_PACKET_SIZE);
    }
    if (this.writeBuffer.position() + len > MAX_PACKET_SIZE) {
      throw new TTransportException(
          TTransportException.UNKNOWN, "Message size too large: " + len + " > " + MAX_PACKET_SIZE);
    }
    this.writeBuffer.put(bytes, offset, len);
  }

  @Override
  public void flush() throws TTransportException {
    if (this.writeBuffer != null) {
      byte[] bytes = new byte[MAX_PACKET_SIZE];
      int len = this.writeBuffer.position();
      this.writeBuffer.flip();
      this.writeBuffer.get(bytes, 0, len);
      try {
        this.socket.send(new DatagramPacket(bytes, len));
      } catch (IOException e) {
        throw new TTransportException(
            TTransportException.UNKNOWN, "Cannot flush closed transport", e);
      } finally {
        this.writeBuffer = null;
      }
    }
  }
}
