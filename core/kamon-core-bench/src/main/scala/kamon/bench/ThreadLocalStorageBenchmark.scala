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

package kamon.bench

import kamon.context.Storage.CrossThreadLocal

import java.util.concurrent.TimeUnit
import kamon.context.{Context, Storage}
import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
class ThreadLocalStorageBenchmark {

  val TestKey: Context.Key[Int] = Context.key("test-key", 0)
  val ContextWithKey: Context = Context.of(TestKey, 43)

  val CrossTLS: Storage = new CrossThreadLocal
  val FTLS: Storage = new Storage.ThreadLocal

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.NANOSECONDS)
  @Fork
  def crossThreadLocal: Context = {
    val scope = CrossTLS.store(ContextWithKey)
    CrossTLS.current()
    scope.close()
    CrossTLS.current()
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.NANOSECONDS)
  @Fork
  def fastThreadLocal: Context = {
    val scope = FTLS.store(ContextWithKey)
    FTLS.current()
    scope.close()
    FTLS.current()
  }
}
