package kamon.instrumentation.logback;

import kanela.agent.libs.net.bytebuddy.asm.Advice;

import java.util.Map;

public class MdcPropertyMapAdvice {

  @Advice.OnMethodExit()
  public static void onExit(@Advice.Return(readOnly = false) java.util.Map<String, String> propertyMap) {
    propertyMap = ContextToMdcPropertyMapAppender.appendContext(propertyMap);
  }
}
