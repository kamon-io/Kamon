package kamon.instrumentation.mongo;

import com.mongodb.MongoNamespace;
import kamon.Kamon;
import kamon.context.Storage;
import kamon.instrumentation.context.HasContext;
import kamon.trace.Span;
import kanela.agent.libs.net.bytebuddy.asm.Advice;

public class BatchCursorGetMoreAdvice {

  @Advice.OnMethodEnter
  public static Storage.Scope enter(
      @Advice.This Object batchCursor,
      @Advice.FieldValue("namespace") MongoNamespace namespace) {

    final Span parentSpan = ((HasContext) batchCursor).context().get(Span.Key());
    final Span getMoreSpan = MongoClientInstrumentation.getMoreSpanBuilder(parentSpan, namespace).start();

    return Kamon.storeContext(Kamon.currentContext().withEntry(Span.Key(), getMoreSpan));
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class)
  public static void exit(@Advice.Enter Storage.Scope scope, @Advice.Thrown Throwable t) {
    final Span span = scope.context().get(Span.Key());

    if(t == null) {
      span.finish();
    } else {
      span.fail(t).finish();
    }

    scope.close();
  }
}
