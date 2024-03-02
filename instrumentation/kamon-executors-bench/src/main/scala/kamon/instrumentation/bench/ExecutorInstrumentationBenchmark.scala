/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
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

package kamon.instrumentation.bench

import java.util.concurrent.{Executor, TimeUnit}

import com.google.common.util.concurrent.MoreExecutors
import kamon.Kamon
import kamon.context.Context
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
  def control(blackhole: Blackhole): Unit = {
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
  def wrapped(blackhole: Blackhole): Unit = {
    MoreExecutors.directExecutor.execute(new Wrapper(new BlackholeRunnable(blackhole)))
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
  def instrumentedViaMixin(blackhole: Blackhole): Unit = {
    MoreExecutors.directExecutor.execute(new InstrumentedViaMixin(blackhole))
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
  def instrumentedViaWrapper(blackhole: Blackhole): Unit = {
    MoreExecutors.directExecutor.execute(new InstrumentedWrapper(new BlackholeRunnable(blackhole)))
  }
}

private class BlackholeRunnable(blackhole: Blackhole) extends Runnable {
  override def run(): Unit = {
    blackhole.consume(Kamon.currentContext())
  }
}

private class Wrapper(runnable: Runnable) extends Runnable {
  override def run(): Unit = runnable.run()
}

class InstrumentedViaMixin(blackhole: Blackhole) extends Runnable {
  val context: Context = Kamon.currentContext()

  override def run(): Unit = {
    val scope = Kamon.storeContext(context)
    blackhole.consume(Kamon.currentContext())
    scope.close()
  }
}

class InstrumentedWrapper(runnable: Runnable) extends Runnable {
  val context: Context = Kamon.currentContext()

  override def run(): Unit = {
    val scope = Kamon.storeContext(context)
    runnable.run()
    scope.close()
  }
}

object DirectExecutor extends Executor {
  override def execute(command: Runnable): Unit =
    command.run()
}
