package kamon.instrumentation.elasticsearch;

import kanela.agent.libs.net.bytebuddy.asm.Advice;

public class HighLevelElasticsearchClientInstrumentation {
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
