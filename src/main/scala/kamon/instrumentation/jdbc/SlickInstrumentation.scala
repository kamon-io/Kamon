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

package kamon.instrumentation.jdbc

import java.util.concurrent.Callable

import kamon.Kamon
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.implementation.bind.annotation.{RuntimeType, SuperCall}
import slick.util.AsyncExecutor

import scala.concurrent.ExecutionContext

class SlickInstrumentation extends InstrumentationBuilder {

  /**
    * This wraps the returned AsyncExecutor with an implementation that captures the current Context when submitting
    * any task to it and sets it as current when the task executes.
    */
  onType("slick.util.AsyncExecutor$")
    .intercept(method("apply").and(takesArguments(7)), AsyncExecutorApplyInterceptor)
}

object AsyncExecutorApplyInterceptor {

  @RuntimeType
  def apply(@SuperCall zuper: Callable[AsyncExecutor]): AsyncExecutor = {
    new SlickInstrumentation.ContextAwareAsyncExecutor(zuper.call())
  }

}



object SlickInstrumentation {

  class ContextAwareAsyncExecutor(underlying: AsyncExecutor) extends AsyncExecutor {
    override def executionContext: ExecutionContext =
      new ContextAwareExecutionContext(underlying.executionContext)

    override def close(): Unit =
      underlying.close()
  }

  class ContextAwareExecutionContext(underlying: ExecutionContext) extends ExecutionContext {
    override def execute(runnable: Runnable): Unit =
      underlying.execute(new ContextAwareRunnable(runnable))

    override def reportFailure(cause: Throwable): Unit =
      underlying.reportFailure(cause)
  }

  class ContextAwareRunnable(underlying: Runnable) extends Runnable {
    private val _context = Kamon.currentContext

    override def run(): Unit = {
      Kamon.storeContext(_context) {
        underlying.run()
      }
    }
  }
}
