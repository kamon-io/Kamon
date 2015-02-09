/* =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License") you may not use this file
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

package kamon.annotation

import java.util.concurrent.{Executors, ExecutorService}
import java.util.regex.{ Matcher, Pattern }
import javax.el.ELProcessor

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import kamon.Kamon
import kamon.annotation.resolver.PrivateFieldELResolver
import kamon.annotation.util.ELProcessorPool

object Annotation extends ExtensionId[AnnotationExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = Annotation
  override def createExtension(system: ExtendedActorSystem): AnnotationExtension = new AnnotationExtension(system)
}

class AnnotationExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  private val config = system.settings.config.getConfig("kamon.annotation")
}

case class Employee(name: String)

object Main extends App {
  val s = Pattern.compile("[#|$]\\{(.*)\\}")

  private val pool: ExecutorService = Executors.newFixedThreadPool(4)
  for(_ <- 0 to 100000000) {
    Thread.sleep(100)
    pool.submit( new Runnable {
      override def run(): Unit = {
        ELProcessorPool.withObject(new Employee("Charlie Brown")) { elp =>
          println(elp.evalToString("#{this.name}") + ":" + Thread.currentThread().getName)
//          val matcher = s.matcher()
//          if (matcher.find()) {
//            val a = evaluateCompositeExpression(elp.processor, matcher)
//            val name = elp.eval(a)
//            println(a + ":" + Thread.currentThread().getName)
        }
      }
    })
  }
//  val elp = new ELProcessor()
//  elp.getELManager.addELResolver(new PrivateFieldELResolver())
//  val matcher = s.matcher("#{employee.name}")
//  if (matcher.find()) {
//    val a = evaluateCompositeExpression(elp, matcher)
//    val name = elp.eval(a)
//    println(a)
//  }

  def evaluateCompositeExpression(processor: ELProcessor, matcher: Matcher): String = {
    val buffer = new StringBuffer();
    do {
      val result = processor.eval(matcher.group(1));
      matcher.appendReplacement(buffer, if (result != null) String.valueOf(result) else "");
    } while (matcher.find());

    matcher.appendTail(buffer);
    return buffer.toString();
  }
}

