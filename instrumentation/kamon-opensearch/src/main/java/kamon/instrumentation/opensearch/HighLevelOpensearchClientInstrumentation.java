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

package kamon.instrumentation.opensearch;

import kanela.agent.libs.net.bytebuddy.asm.Advice;

public class HighLevelOpensearchClientInstrumentation {
    public static final ThreadLocal<String> requestClassName = new ThreadLocal<>();

    @Advice.OnMethodEnter
    public static <Req> void enter(
            @Advice.Argument(0) Req request) {
        requestClassName.set(request.getClass().getSimpleName());
    }

    @Advice.OnMethodExit
    public static void exit() {
        requestClassName.remove();
    }
}
