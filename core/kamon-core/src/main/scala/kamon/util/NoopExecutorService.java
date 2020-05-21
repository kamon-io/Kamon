package kamon.util;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * An executor service that does nothing. Used with empty metrics so that no
 * threads are created wasting time / space.
 * 
 * From https://giraph.apache.org/xref/com/yammer/metrics/core/NoOpExecutorService.html
 */
public class NoopExecutorService implements ScheduledExecutorService {
  @Override
  public ScheduledFuture<?> schedule(Runnable runnable, long l,
                                     TimeUnit timeUnit) {
    return null;
  }

  @Override
  public <V> ScheduledFuture<V> schedule(Callable<V> vCallable, long l,
                                         TimeUnit timeUnit) {
    return null;
  }

  @Override
  public ScheduledFuture<?> scheduleAtFixedRate(
    Runnable runnable, long l, long l1, TimeUnit timeUnit
  ) {
    return null;
  }

  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(
    Runnable runnable, long l, long l1, TimeUnit timeUnit
  ) {
    return null;
  }

  @Override
  public void shutdown() {
  }

  @Override
  public List<Runnable> shutdownNow() {
    return null;
  }

  @Override
  public boolean isShutdown() {
    return false;
  }

  @Override
  public boolean isTerminated() {
    return false;
  }

  @Override
  public boolean awaitTermination(long l, TimeUnit timeUnit)
    throws InterruptedException {
    return false;
  }

  @Override
  public <T> Future<T> submit(Callable<T> tCallable) {
    return null;
  }

  @Override
  public <T> Future<T> submit(Runnable runnable, T t) {
    return null;
  }

  @Override
  public Future<?> submit(Runnable runnable) {
    return null;
  }

  @Override
  public <T> List<Future<T>> invokeAll(
    Collection<? extends Callable<T>> callables)
    throws InterruptedException {
    return null;
  }

  @Override
  public <T> List<Future<T>> invokeAll(
    Collection<? extends Callable<T>> callables, long l, TimeUnit timeUnit
  ) throws InterruptedException {
    return null;
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> callables)
    throws InterruptedException, ExecutionException {
    return null;
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> callables, long l,
                         TimeUnit timeUnit)
    throws InterruptedException, ExecutionException, TimeoutException {
    return null;
  }

  @Override
  public void execute(Runnable runnable) {
  }
}
