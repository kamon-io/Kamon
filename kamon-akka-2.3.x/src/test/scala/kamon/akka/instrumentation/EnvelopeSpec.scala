/*
 * =========================================================================================
 * Copyright © 2017 the kamon project <http://kamon.io/>
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

package kamon.instrumentation.akka


import akka.actor.{ActorSystem, ExtendedActorSystem, Props}
import akka.dispatch.Envelope
import akka.kamon.instrumentation.{InstrumentedEnvelope, TimestampedContext}
import akka.testkit.{ImplicitSender, TestKit}
import kamon.Kamon
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class EnvelopeSpec extends TestKit(ActorSystem("EnvelopeSpec")) with WordSpecLike with Matchers
    with BeforeAndAfterAll with ImplicitSender {

  "EnvelopeInstrumentation" should {
    "mixin EnvelopeContext" in {
      val actorRef = system.actorOf(Props[NoReply])
      val env = Envelope("msg", actorRef, system).asInstanceOf[Object]
      env match {
        case e: Envelope with InstrumentedEnvelope => e.setTimestampedContext(TimestampedContext(Kamon.clock().nanos(), Kamon.currentContext()))
        case _ => sys.error("InstrumentedEnvelope is not mixed in")
      }
      env match {
        case s: Serializable => {
          import java.io._
          val bos = new ByteArrayOutputStream
          val oos = new ObjectOutputStream(bos)
          oos.writeObject(env)
          oos.close()
          akka.serialization.JavaSerializer.currentSystem.withValue(system.asInstanceOf[ExtendedActorSystem])  {
            val ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))
            val obj = ois.readObject()
            ois.close()
            obj match {
              case e: Envelope with InstrumentedEnvelope => e.timestampedContext() should not be null
              case _ => sys.error("InstrumentedEnvelope is not mixed in")
            }
          }
        }
        case _ => sys.error("envelope is not serializable")
      }
    }
  }
}
