/*
 * Copyright 2013-2023 The Kamon Project <https://kamon.io>
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

package kamon.instrumentation.executor;

import kanela.agent.libs.net.bytebuddy.asm.Advice;
import scala.concurrent.ExecutionContext$;
import scala.concurrent.impl.ExecutionContextImpl;

import java.util.concurrent.ExecutorService;

import static kanela.agent.libs.net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC;

final class ScalaGlobalExecutionContextAdvice {

    @Advice.OnMethodExit
    public static void onExit(@Advice.Return(readOnly = false, typing = DYNAMIC) ExecutionContextImpl returnValue) {
        ExecutorService instrumented = ExecutorInstrumentation.instrument((ExecutorService) returnValue.executor(), "scala-global-execution-context");
        returnValue = new ExecutionContextImpl(instrumented, ExecutionContext$.MODULE$.defaultReporter());
    }
}
