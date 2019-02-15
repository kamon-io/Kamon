/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon.jdbc.instrumentation

import kamon.Kamon
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._
import slick.util.AsyncExecutor

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

@Aspect
class SlickInstrumentation {

  @Pointcut("execution(* slick.util.AsyncExecutor$.apply(..)) && args(name, minThreads, maxThreads, queueSize, maxConnections, keepAliveTime, registerMbeans)")
  def asyncExecutorCreation(name: String, minThreads: Int, maxThreads: Int, queueSize: Int, maxConnections: Int,
      keepAliveTime: Duration, registerMbeans: Boolean): Unit = {}

  @Around("asyncExecutorCreation(name, minThreads, maxThreads, queueSize, maxConnections, keepAliveTime, registerMbeans)")
  def afterReturningHikariPoolConstructor(pjp: ProceedingJoinPoint, name: String, minThreads: Int, maxThreads: Int,
      queueSize: Int, maxConnections: Int, keepAliveTime: Duration, registerMbeans: Boolean): AsyncExecutor = {
    new SlickInstrumentation.ContextAwareAsyncExecutor(pjp.proceed().asInstanceOf[AsyncExecutor])
  }
}

object SlickInstrumentation {

  class ContextAwareAsyncExecutor(underlying: AsyncExecutor) extends AsyncExecutor {
    override def executionContext: ExecutionContext = new ContextAwareExecutionContext(underlying.executionContext)
    override def close(): Unit                      = underlying.close()
  }

  class ContextAwareExecutionContext(underlying: ExecutionContext) extends ExecutionContext {
    override def execute(runnable: Runnable): Unit     = underlying.execute(new ContextAwareRunnable(runnable))
    override def reportFailure(cause: Throwable): Unit = underlying.reportFailure(cause)
  }

  class ContextAwareRunnable(underlying: Runnable) extends Runnable {
    private val traceContext = Kamon.currentContext

    override def run(): Unit = {
      Kamon.withContext(traceContext) {
        underlying.run()
      }
    }
  }
}
