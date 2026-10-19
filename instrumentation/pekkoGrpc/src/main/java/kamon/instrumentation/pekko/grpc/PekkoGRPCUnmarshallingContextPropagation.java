package kamon.instrumentation.pekko.grpc;

import org.apache.pekko.http.javadsl.model.HttpEntity;
import kamon.Kamon;
import kamon.context.Context;
import kanela.agent.libs.net.bytebuddy.asm.Advice;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class PekkoGRPCUnmarshallingContextPropagation {

  @Advice.OnMethodExit()
  public static void onExit(
      @Advice.Return(readOnly = false) CompletionStage<?> returnValue,
      @Advice.Argument(0) Object firstArgument) {

    if(firstArgument instanceof HttpEntity && returnValue instanceof CompletableFuture) {
      final Context currentContext = Kamon.currentContext();

      // NOTES: The wrapper is only overriding thenCompose because it is the only function that gets called
      //        after GrpcMarshalling.unmarshall in the auto-generated HandlerFactory for gRPC services. In
      //        the future this might be removed if we instrument CompletionStage directly.
      returnValue = new ContextPropagatingCompletionStage<>((CompletableFuture) returnValue, currentContext);
    }
  }


  public static class ContextPropagatingCompletionStage<T> extends CompletableFuture<T> {
    private final CompletableFuture<T> wrapped;
    private final Context context;

    public ContextPropagatingCompletionStage(CompletableFuture<T> wrapped, Context context) {
      this.wrapped = wrapped;
      this.context = context;
    }

    @Override
    public <U> CompletableFuture<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
      Function<? super T, ? extends CompletionStage<U>> wrapperFunction = (t) -> {
        return Kamon.runWithContext(context, () -> fn.apply(t));
      };

      return wrapped.thenCompose(wrapperFunction);
    }
  }

}
