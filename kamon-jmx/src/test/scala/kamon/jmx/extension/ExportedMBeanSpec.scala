/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

package kamon.jmx.extension

import java.lang.management.ManagementFactory
import javax.management.{ MBeanServer, ObjectName }

import kamon.jmx.extension._
import kamon.testkit.BaseKamonSpec

import com.typesafe.config.ConfigFactory

import org.scalatest.{ Matchers, WordSpec }

// a test fixture mbean
class Test(val intValues: Seq[Int], val longValues: Seq[Long], val name: String)
    extends TestMBean {

  require(intValues.size == longValues.size)

  var intIdx: Int = 0
  var longIdx: Int = 0

  def getIntValue(): Int = {
    val ret: Int = intValues(intIdx)
    intIdx = intIdx + 1
    ret
  }

  def getLongValue(): Long = {
    val ret: Long = longValues(longIdx)
    longIdx = longIdx + 1
    ret
  }

  def unregister(): Unit = {
    val server: MBeanServer = ManagementFactory.getPlatformMBeanServer()
    try {
      server.unregisterMBean(
        new ObjectName("test:type=testBean,name=" + name))
    } catch {
      // we don't care if we never registered for some weird reason
      case e: javax.management.InstanceNotFoundException ⇒ {}
    }
  }

  // register ourselves with the JVM JMX infrastructure
  val server: MBeanServer = ManagementFactory.getPlatformMBeanServer()
  server.registerMBean(
    this, new ObjectName("test:type=testBean,name=" + name))
}

class ExportedMBeanSpec extends BaseKamonSpec("exported-mbean-spec") {

  "ExportedMBeanQuery" should {
    "read metrics from a mbean" in {

      val intValues: Seq[Int] = for (i ← 0 to 9) yield i
      val longValues: Seq[Long] = for (i ← 0 to 9) yield i.toLong
      val testMBean = new Test(intValues, longValues, "test-fixture")

      Thread.sleep(1000)

      for (i ← 0 to 9) {
        val longI: Long = i.toLong
        val snapshot = takeSnapshotOf("test-fixture", "kamon-mxbeans")
        snapshot.counter("IntValue").get.count should be(i)
        snapshot.counter("LongValue").get.count should be(longI)
        Thread.sleep(1000) // wait for next tick
      }

      testMBean.unregister()
    }
  }
}
