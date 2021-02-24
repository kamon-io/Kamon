package kamon.instrumentation.spring.server;

import kanela.agent.libs.net.bytebuddy.asm.Advice;
import java.util.concurrent.Callable;

public class CallableWrapper {
    @Advice.OnMethodEnter()
    // raw use of parametrized class?
    public static void enter(@Advice.Argument(value = 0, readOnly = false) Callable callable) {
        callable = CallableContextWrapper.wrap(callable);
    }

}
