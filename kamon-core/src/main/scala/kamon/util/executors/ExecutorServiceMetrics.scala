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

package kamon.util.executors

import kamon.Kamon
import kamon.metric.Entity
import java.util.concurrent.{ ForkJoinPool ⇒ JavaForkJoinPool }
import java.util.concurrent.{ ExecutorService, ThreadPoolExecutor }

import scala.concurrent.forkjoin.ForkJoinPool
import scala.util.control.NoStackTrace

object ExecutorServiceMetrics {
  val Category = "executor-service"

  private val DelegatedExecutor = Class.forName("java.util.concurrent.Executors$DelegatedExecutorService")
  private val FinalizableDelegated = Class.forName("java.util.concurrent.Executors$FinalizableDelegatedExecutorService")
  private val DelegateScheduled = Class.forName("java.util.concurrent.Executors$DelegatedScheduledExecutorService")
  private val JavaForkJoinPool = classOf[JavaForkJoinPool]
  private val ScalaForkJoinPool = classOf[ForkJoinPool]

  private val executorField = {
    // executorService is private :(
    val field = DelegatedExecutor.getDeclaredField("e")
    field.setAccessible(true)
    field
  }

  /**
   *
   * Register the [[http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ThreadPoolExecutor.html ThreadPoolExecutor]] to Monitor.
   *
   * @param name The name of the [[ThreadPoolExecutor]]
   * @param threadPool The intance of the [[ThreadPoolExecutor]]
   * @param tags The tags associated to the [[ThreadPoolExecutor]]
   */
  private def registerThreadPool(name: String, threadPool: ThreadPoolExecutor, tags: Map[String, String]): Unit = {
    Kamon.metrics.entity(ThreadPoolExecutorMetrics.factory(threadPool, Category), Entity(name, Category, tags))
  }

  /**
   *
   * Register the [[http://www.scala-lang.org/api/current/index.html#scala.collection.parallel.TaskSupport ForkJoinPool]] to Monitor.
   *
   * @param name The name of the [[ForkJoinPool]]
   * @param forkJoinPool The instance of the [[ForkJoinPool]]
   * @param tags The tags associated to the [[ForkJoinPool]]
   */
  private def registerScalaForkJoin(name: String, forkJoinPool: ForkJoinPool, tags: Map[String, String] = Map.empty): Unit = {
    Kamon.metrics.entity(ForkJoinPoolMetrics.factory(forkJoinPool, Category), Entity(name, Category, tags))
  }

  /**
   *
   * Register the [[https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ForkJoinPool.html JavaForkJoinPool]] to Monitor.
   *
   * @param name The name of the [[JavaForkJoinPool]]
   * @param forkJoinPool The instance of the [[JavaForkJoinPool]]
   * @param tags The tags associated to the [[JavaForkJoinPool]]
   */
  private def registerJavaForkJoin(name: String, forkJoinPool: JavaForkJoinPool, tags: Map[String, String] = Map.empty): Unit = {
    Kamon.metrics.entity(ForkJoinPoolMetrics.factory(forkJoinPool, Category), Entity(name, Category, tags))
  }

  /**
   *
   * Register the [[https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html ExecutorService]] to Monitor.
   *
   * @param name The name of the [[ExecutorService]]
   * @param executorService The instance of the [[ExecutorService]]
   * @param tags The tags associated to the [[ExecutorService]]
   */
  def register(name: String, executorService: ExecutorService, tags: Map[String, String]): Unit = executorService match {
    case threadPoolExecutor: ThreadPoolExecutor ⇒ registerThreadPool(name, threadPoolExecutor, tags)
    case scalaForkJoinPool: ForkJoinPool if scalaForkJoinPool.getClass.isAssignableFrom(ScalaForkJoinPool) ⇒ registerScalaForkJoin(name, scalaForkJoinPool, tags)
    case javaForkJoinPool: JavaForkJoinPool if javaForkJoinPool.getClass.isAssignableFrom(JavaForkJoinPool) ⇒ registerJavaForkJoin(name, javaForkJoinPool, tags)
    case delegatedExecutor: ExecutorService if delegatedExecutor.getClass.isAssignableFrom(DelegatedExecutor) ⇒ registerDelegatedExecutor(name, delegatedExecutor, tags)
    case delegatedScheduledExecutor: ExecutorService if delegatedScheduledExecutor.getClass.isAssignableFrom(DelegateScheduled) ⇒ registerDelegatedExecutor(name, delegatedScheduledExecutor, tags)
    case finalizableDelegatedExecutor: ExecutorService if finalizableDelegatedExecutor.getClass.isAssignableFrom(FinalizableDelegated) ⇒ registerDelegatedExecutor(name, finalizableDelegatedExecutor, tags)
    case other ⇒ throw new NotSupportedException(s"The ExecutorService $name is not supported.")
  }

  //Java variants
  def register(name: String, executorService: ExecutorService): Unit = {
    register(name, executorService, Map.empty[String, String])
  }

  def register(name: String, executorService: ExecutorService, tags: java.util.Map[String, String]): Unit = {
    import scala.collection.JavaConverters._
    register(name, executorService, tags.asScala.toMap)
  }

  /**
   *
   * Remove the [[https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html ExecutorService]] to Monitor.
   *
   * @param name The name of the [[ExecutorService]]
   * @param tags The tags associated to the [[ExecutorService]]
   */
  def remove(name: String, tags: Map[String, String]): Unit = {
    Kamon.metrics.removeEntity(name, Category, tags)
  }

  //Java variants
  def remove(name: String): Unit = {
    remove(name, Map.empty[String, String])
  }

  def remove(name: String, tags: java.util.Map[String, String]): Unit = {
    import scala.collection.JavaConverters._
    remove(name, tags.asScala.toMap)
  }

  /**
   * INTERNAL USAGE ONLY
   */
  private def registerDelegatedExecutor(name: String, executor: ExecutorService, tags: Map[String, String]) = {
    val underlyingExecutor = executorField.get(executor) match {
      case executorService: ExecutorService ⇒ executorService
      case other                            ⇒ other
    }
    register(name, underlyingExecutor.asInstanceOf[ExecutorService], tags)
  }

  case class NotSupportedException(message: String) extends RuntimeException with NoStackTrace
}