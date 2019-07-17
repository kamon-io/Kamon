/*
 * =========================================================================================
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

package kamon.annotation.instrumentation.advisor;

import kamon.Kamon;
import kamon.annotation.api.SpanCustomizer;
import kamon.context.Storage;
import kanela.agent.libs.net.bytebuddy.asm.Advice;

import java.lang.reflect.Method;

public final class SpanCustomizerAnnotationAdvisor {
    @Advice.OnMethodEnter
    public static void onEnter(@Advice.Origin Method method,
                               @Advice.Local("scope") Storage.Scope scope) {

        final SpanCustomizer annotation = method.getAnnotation(SpanCustomizer.class);
        scope = Kamon.store(Kamon.currentContext().withKey(kamon.annotation.util.Hooks.key(), kamon.annotation.util.Hooks.updateOperationName(annotation.operationName())));
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.Local("scope") Storage.Scope scope) {
        scope.close();
    }
}