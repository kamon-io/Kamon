/* =========================================================================================
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


package kamon.executors.bench

import java.util.concurrent.{Executor, ExecutorService, TimeUnit}

import com.google.common.util.concurrent.MoreExecutors
import kamon.Kamon
import kamon.executors.util.ContextAwareRunnable
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

class ExecutorInstrumentationBenchmark {

  /**
    * This benchmark attempts to measure the performance without any context propagation.
    *
    * @param blackhole a { @link Blackhole} object supplied by JMH
    */
  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.NANOSECONDS)
  @Fork
  def none(blackhole: Blackhole): Unit = {
    MoreExecutors.directExecutor.execute(new BlackholeRunnable(blackhole))
 }

  /**
    * This benchmark attempts to measure the performance with manual context propagation.
    *
    * @param blackhole a { @link Blackhole} object supplied by JMH
    */
  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.NANOSECONDS)
  @Fork
  def manual(blackhole: Blackhole): Unit = {
    MoreExecutors.directExecutor.execute(new ContextAwareRunnable(new BlackholeRunnable(blackhole)))
  }

  /**
    * This benchmark attempts to measure the performance with automatic context propagation.
    *
    * @param blackhole a { @link Blackhole} object supplied by JMH
    */
  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.NANOSECONDS)
  @Fork(jvmArgsAppend = Array("-javaagent:/home/diego/.m2/repository/io/kamon/kanela-agent/0.0.300/kanela-agent-0.0.300.jar"))
  def automatic(blackhole: Blackhole): Unit = {
    MoreExecutors.directExecutor.execute(new BlackholeRunnable(blackhole))
  }
}

private class BlackholeRunnable(blackhole: Blackhole) extends Runnable {
  override def run(): Unit = {
    blackhole.consume(Kamon.currentContext())
  }
}

object DirectExecutor extends Executor {
  override def execute(command: Runnable): Unit =
    command.run()
}
