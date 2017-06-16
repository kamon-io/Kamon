#Executor Service Metrics Tracker <img align="right" src="https://rawgit.com/kamon-io/Kamon/master/kamon-logo.svg" height="150px" style="padding-left: 20px"/> 
[![Build Status](https://travis-ci.org/kamon-io/kamon-executors.svg?branch=master)](https://travis-ci.org/kamon-io/kamon-executors)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kamon-io/Kamon?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-executors_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-executors_2.11)


### Getting Started

Executor Service Metrics is currently available for Scala 2.10, 2.11 and 2.12.

Supported releases and dependencies are shown below.

| kamon  | status | jdk  | scala            
|:------:|:------:|:----:|------------------
|  1.0.0 | stable | 1.8+ | 2.10, 2.11, 2.12

To get started with SBT, simply add the following to your `build.sbt`
file:

```scala
libraryDependencies += "io.kamon" %% "kamon-executors" % "1.0.0"
```

### Documentation

### Thread Pools ###

A __Thread Pool__ is represented by a instance of the class [ExecutorService]. Using a [ExecutorService], you can submit a series of tasks that will be completed in the future.

[Executors] class provides some factory methods in order to create types of thread pools as following:

* __Single Thread Executor:__ A thread pool with only one thread.
* __Cached Thread Pool:__ A thread pool that create as many threads it needs to execute the task in parallel.
* __Fixed Thread Pool:__ A thread pool with a fixed number of threads.
* __Scheduled Thread Pool:__ A thread pool made to schedule future task.
* __Single Thread Scheduled Pool:__ A thread pool with only one thread to schedule future task.
* __Work Stealing Pool:__ Creates a work-stealing thread pool using all available processors as its target parallelism level.

### Thread Pool Metrics ###
The metrics provided for each __Thread Pool__ will change depending on the type of pool at hand. To know for sure what kind of pool you are looking at, we will always include a tag named `executor-type` whose value will always be
present and be either `fork-join-pool` for `thread-pool-executor`, matching the type of thread pool that you actually
created.

### Fork Join Pool ###

When your pool type is `fork-join-pool` you will get:

* __parallelism__: a min max counter with the desired parallelism value. This value will remain steady over time.
* __pool-size__: a gauge tracking the number of worker threads that have started but not yet terminated. This value will
differ that of `parallelism` if threads are created to maintain parallelism when others are cooperatively blocked.
* __active-threads__: a gauge tracking an estimate of the number of threads that are currently stealing or executing
tasks.
* __running-threads__: a gauge tracking an estimate of the number of worker threads that are not blocked waiting to join
tasks or for other managed synchronization.
* __queued-task-count__: a gauge tracking  the total number of tasks currently held in queues by worker threads (but not
including tasks submitted to the pool that have not begun executing). This value is only an approximation, obtained by
iterating across all threads in the pool.


### Thread Pool Executor ###

When your pool type is `thread-pool-executor` you will get:

* __core-pool-size__: a gauge tracking the core pool size of the executor.
* __max-pool-size__: a gauge tracking the maximum number of threads allowed by the executor.
* __pool-size__: a gauge tracking the current number of threads in the pool.
* __active-threads__: a gauge tracking the number of threads that are actively executing tasks.
* __processed-tasks__: a gauge tracking the number of processed tasks for the executor. Please not that even while the
`ThreadPoolExecutor` provides us with the total number of tasks ever processed by the executor, this metrics is effectively
tracking the number of tasks as a differential from the last recorded value.

__Thread Pools__ are very important to execute synchronous and asynchronous processes in our applications and for that reason `Kamon` provides a simple way to monitoring them.


### Scala example ###

```scala
val forkJoinPool = Executors.newWorkStealingPool()
val fixedThreadPool = Executors.newFixedThreadPool(10)

Executors.register("java-fork-join-pool", forkJoinPool)
Executors.register("fixed-thread-pool", fixedThreadPool)

for (_ <- 0 to 100) {
  forkJoinPool.submit(HeavyWeightTask())
  fixedThreadPool.submit(HeavyWeightTask())
}

forkJoinPool.shutdown()
fixedThreadPool.shutdown()

```

### Java example ###

```java
final ExecutorService forkJoinPool = Executors.newWorkStealingPool();
final ExecutorService fixedThreadPool = Executors.newFixedThreadPool(10);

Executors.register("fork-join-pool", forkJoinPool);
Executors.register("fixed-thread-pool", fixedThreadPool);

for(int i = 0; i < 100; i++) {
    forkJoinPool.submit(new HeavyWeightTask());
    fixedThreadPool.submit(new HeavyWeightTask());
}

forkJoinPool.shutdown();
fixedThreadPool.shutdown();
```

The output for example above using the `kamon-log-reporter` module should be like the following:

```text
+------------------------------------------------------------------------------------------------+
|  Thread-Pool-Executor                                                                          |
|                                                                                                |
|  Name: fixed-thread-pool                                                                       |
|                                                                                                |
|  Core Pool Size: 10                                                                            |
|  Max  Pool Size: 10                                                                            |
|                                                                                                |
|                                                                                                |
|                         Pool Size        Active Threads          Processed Task                |
|           Min              10                  0                      0                        |
|           Avg              10.0                1.0                    0.0                      |
|           Max              10                  10                     0                        |
|                                                                                                |
+------------------------------------------------------------------------------------------------+
+------------------------------------------------------------------------------------------------+
|  Fork-Join-Pool                                                                                |
|                                                                                                |
|  Name: fork-join-pool                                                                          |
|                                                                                                |
|  Paralellism: 4                                                                                |
|                                                                                                |
|                 Pool Size       Active Threads     Running Threads     Queue Task Count        |
|      Min           2                 0                   0                   0                 |
|      Avg           3.0               0.0                 1.0                 0.0               |
|      Max           4                 0                   4                   0                 |
|                                                                                                |
+------------------------------------------------------------------------------------------------+

```

[ExecutorService]: https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html
[Executors]: https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/Executors.html