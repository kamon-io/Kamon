/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

package kamon.spm

import org.apache.thrift.TSerializer
import org.apache.thrift.protocol.TBinaryProtocol

object ThriftUtils {

  private val BINARY_PROTOCOL_SERIALIZER = new ThreadLocal[TSerializer]() {

    protected override def initialValue(): TSerializer = {
      new TSerializer(new TBinaryProtocol.Factory())
    }
  }

  def binaryProtocolSerializer(): TSerializer = BINARY_PROTOCOL_SERIALIZER.get
}

