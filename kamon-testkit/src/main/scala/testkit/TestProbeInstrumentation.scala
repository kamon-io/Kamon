/*
 * =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
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

package akka.testkit

import org.aspectj.lang.annotation._
import kamon.trace.{ EmptyTraceContext, TraceContextAware, TraceRecorder }
import org.aspectj.lang.ProceedingJoinPoint
import akka.testkit.TestActor.RealMessage

@Aspect
class TestProbeInstrumentation {

  @DeclareMixin("akka.testkit.TestActor.RealMessage")
  def mixin: TraceContextAware = TraceContextAware.default

  @Pointcut("execution(akka.testkit.TestActor.RealMessage.new(..)) && this(ctx)")
  def realMessageCreation(ctx: TraceContextAware): Unit = {}

  @After("realMessageCreation(ctx)")
  def afterRealMessageCreation(ctx: TraceContextAware): Unit = {
    // Necessary to force the initialization of ContextAware at the moment of creation.
    ctx.traceContext
  }

  @Pointcut("execution(* akka.testkit.TestProbe.reply(..)) && this(testProbe)")
  def testProbeReply(testProbe: TestProbe): Unit = {}

  @Around("testProbeReply(testProbe)")
  def aroundTestProbeReply(pjp: ProceedingJoinPoint, testProbe: TestProbe): Any = {
    val traceContext = testProbe.lastMessage match {
      case msg: RealMessage ⇒ msg.asInstanceOf[TraceContextAware].traceContext
      case _                ⇒ EmptyTraceContext
    }

    TraceRecorder.withTraceContext(traceContext) {
      pjp.proceed
    }
  }

}
