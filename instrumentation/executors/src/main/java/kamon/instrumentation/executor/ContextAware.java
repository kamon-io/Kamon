package kamon.instrumentation.executor;

import kamon.Kamon;
import kamon.context.Context;
import kamon.context.Storage;

import java.util.concurrent.Callable;

/**
 * The idea is to make it possible to extend wrappers over  {@link Runnable} and {@link Callable},
 * so that  {@link ClassCastException} can be avoided when instrumenting implementations of
 * {@link java.util.concurrent.Executor}, {@link java.util.concurrent.ExecutorService#submit(Runnable)},
 * {@link java.util.concurrent.ExecutorService#submit(Callable)},
 * {@link java.util.concurrent.ExecutorService#invokeAll}.
 * <p>
 * {@link ContextAwareRunnable} interface for wrappers with the {@link Runnable} type.
 * <p>
 * {@link ContextAwareCallable} interface for wrappers with the {@link Callable} type.
 * <p>
 * The specified interfaces implement the logic of the {@link Runnable#run()} and {@link Callable#call()} methods as the default method.
 * Thus, a specific implementation should implement {@link ContextAwareRunnable} or {@link ContextAwareCallable}, as well as the necessary interfaces,
 * which will avoid the error {@link ClassCastException} after code instrumentation.
 * <p>
 * Using {@link ContextAwareRunnableProvider}, {@link ContextAwareCallableProvider}, you can provide a specific implementation
 * for {@link ContextAwareRunnable} and {@link ContextAwareCallable}. To do this, you need to implement a provider, you must have a default constructor!
 * In the configuration <pre>kanela.modules.executor-service-capture-on-submit.context-aware-runnable-providers</pre> or
 * <pre>kanela.modules.executor-service-capture-on-submit.context-aware-callable-providers</pre> specify the provider.
 * <p>
 * Example:
 * <p>
 * <pre>
 * kanela.modules.executor-service-capture-on-submit.context-aware-runnable-providers = [
 *   "instrumentation.executor.SlickContextAwareRunnableProvider"
 * ]
 * </pre>
 * <p>
 * When executing the code, {@link ContextAwareRunnableProvider#test(Runnable)}, {@link ContextAwareCallableProvider#test(Callable)} is called.
 * The first provider is used, for which test returns true.
 * <p>
 * If no matches are found, {@link DefaultContextAwareRunnable}, {@link DefaultContextAwareCallable} are used by default.
 * <p>
 * An example for a PrioritizedRunnable from the slick library.
 * If you use the standard implementation, the error {@link ClassCastException} will occur when trying to put a task in a queue,
 * because the queue is parameterized by the PrioritizedRunnable type.
 * Therefore, a class is created that implements {@link ContextAwareRunnable} and PrioritizedRunnable:
 * <p>
 * <pre>
 * {@code
 * import kamon.Kamon;
 * import kamon.context.Context;
 * import slick.util.AsyncExecutor;
 * import ru.tele2.ds.kamon.executor.ContextAware.ContextAwareRunnable;
 * import slick.util.AsyncExecutor.PrioritizedRunnable;
 * public class SlickContextAwareRunnable implements ContextAwareRunnable, PrioritizedRunnable {
 *
 *     private final PrioritizedRunnable underlying;
 *     private final Context context;
 *
 *     public SlickContextAwareRunnable(Runnable r) {
 *         this.context = Kamon.currentContext();
 *         this.underlying = (PrioritizedRunnable) r;
 *     }
 *
 *     @Override
 *     public Runnable getUnderlying() {
 *         return underlying;
 *     }
 *
 *     @Override
 *     public Context getContext() {
 *         return context;
 *     }
 *
 *     @Override
 *     public AsyncExecutor.Priority priority() {
 *         return underlying.priority();
 *     }
 *
 *     @Override
 *     public boolean connectionReleased() {
 *         return underlying.connectionReleased();
 *     }
 *
 *     @Override
 *     public void connectionReleased_$eq(boolean connectionReleased) {
 *         underlying.connectionReleased_$eq(connectionReleased);
 *     }
 *
 *     @Override
 *     public void inUseCounterSet_$eq(boolean inUseCounterSet) {
 *         underlying.inUseCounterSet_$eq(inUseCounterSet);
 *     }
 *
 *     @Override
 *     public boolean inUseCounterSet() {
 *         return underlying.inUseCounterSet();
 *     }
 * }
 * }
 * </pre>
 * <p>
 * Next, we set a new implementation through the provider:
 * <p>
 * <pre>
 * {@code
 * public class SlickContextAwareRunnableProvider implements ContextAware.ContextAwareRunnableProvider {
 *     @Override
 *     public ContextAware.ContextAwareRunnable provide(Runnable original) {
 *         return new SlickContextAwareRunnable(original);
 *     }
 *     @Override
 *     public boolean test(Runnable r) {
 *         return r instanceof AsyncExecutor.PrioritizedRunnable;
 *     }
 * }
 * }
 * </pre>
 * <p>
 * Setting the provider class in the configuration:
 * <p>
 * <pre>
 * kanela.modules.executor-service-capture-on-submit.context-aware-runnable-providers = [
 *   "instrumentation.executor.SlickContextAwareRunnableProvider"
 * ]
 * </pre>
 */
public final class ContextAware {

    private ContextAware() {
        // nothing
    }

    /**
     * Wrapper over {@link Runnable}.
     * Specifies the default implementation of the {@link Runnable#run} method to run in the Kamon {@link Context}.
     */
    public interface ContextAwareRunnable extends Runnable {

        /**
         * @return original {@link Runnable}
         */
        Runnable getUnderlying();

        /**
         * @return current Kamon {@link Context}
         */
        Context getContext();

        @Override
        default void run() {
            final Storage.Scope scope = Kamon.storeContext(getContext());
            try {
                getUnderlying().run();
            } finally {
                scope.close();
            }
        }
    }

    /**
     * Wrapper over {@link Callable}.
     * Specifies the default implementation of the {@link Callable#call} method to run in the Kamon {@link Context}.
     * @param <A> result type
     */
    public interface ContextAwareCallable<A> extends Callable<A> {

        /**
         * @return original {@link Callable}
         */
        Callable<A> getUnderlying();

        /**
         * @return current Kamon {@link Context}
         */
        Context getContext();

        @Override
        default A call() throws Exception {
            final Storage.Scope scope = Kamon.storeContext(getContext());
            try {
                return getUnderlying().call();
            } finally {
                scope.close();
            }
        }
    }

    /**
     * Default implementation {@link ContextAwareRunnable}.
     */
    public static final class DefaultContextAwareRunnable implements ContextAwareRunnable {

        private final Runnable underlying;
        private final Context context;

        public DefaultContextAwareRunnable(Runnable r) {
            this.context = Kamon.currentContext();
            this.underlying = r;
        }

        @Override
        public Runnable getUnderlying() {
            return underlying;
        }

        @Override
        public Context getContext() {
            return context;
        }
    }

    /**
     * Default implementation {@link ContextAwareCallable}.
     *
     * @param <A> result type
     */
    public static final class DefaultContextAwareCallable<A> implements ContextAwareCallable<A> {

        private final Callable<A> underlying;
        private final Context context;

        public DefaultContextAwareCallable(Callable<A> c) {
            this.context = Kamon.currentContext();
            this.underlying = c;
        }

        @Override
        public Callable<A> getUnderlying() {
            return underlying;
        }

        @Override
        public Context getContext() {
            return context;
        }
    }

    /**
     * Provides a method for creating {@link ContextAwareRunnable}.
     * The implementation must have a default constructor!
     */
    public interface ContextAwareRunnableProvider {

        /**
         * @param original {@link Runnable}
         * @return {@link ContextAwareRunnable}
         */
        ContextAwareRunnable provide(Runnable original);

        /**
         * Check that the provider can provide the wrapper implementation.
         *
         * @param r {@link Runnable}
         * @return true, if it can provide a wrapper implementation
         */
        boolean test(Runnable r);
    }

    /**
     * Provides a method for creating {@link ContextAwareCallable}.
     * The implementation must have a default constructor!
     */
    public interface ContextAwareCallableProvider {

        /**
         * @param original {@link Callable}
         * @param <A> result type
         * @return {@link ContextAwareCallable}
         */
        <A> ContextAwareCallable<A> provide(Callable<A> original);

        /**
         * Check that the provider can provide the wrapper implementation.
         *
         * @param r {@link Callable}
         * @return true, if it can provide a wrapper implementation
         */
        boolean test(Callable<?> r);
    }
}
