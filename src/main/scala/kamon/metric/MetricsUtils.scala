package kamon.metric

import com.codahale.metrics._

object MetricsUtils {

  def markMeter[T](meter:Meter)(f: => T): T = {
    meter.mark()
    f
  }
//
//  def incrementCounter(key: String) {
//    counters.getOrElseUpdate(key, (metricsGroup.counter(s"${key}-counter"))).count
//  }
//
//  def markMeter(key: String) {
//    meters.getOrElseUpdate(key, metricsGroup.meter(s"${key}-meter", "actor", "actor-message-counter", TimeUnit.SECONDS)).mark()
//  }
//
//  def trace[T](key: String)(f: => T): T = {
//    val timer =  timers.getOrElseUpdate(key, (metricsGroup.timer(s"${key}-timer")) )
//    timer.time(f)
//  }

//  def markAndCountMeter[T](key: String)(f: => T): T = {
//    markMeter(key)
//    f
//  }
//
//  def traceAndCount[T](key: String)(f: => T): T = {
//    incrementCounter(key)
//    trace(key) {
//      f
//    }
  //}

//  implicit def runnable(f: () => Unit): Runnable =
//    new Runnable() { def run() = f() }
//
//
//  import java.util.concurrent.Callable
//
//  implicit def callable[T](f: () => T): Callable[T] =
//    new Callable[T]() { def call() = f() }

//  private val actorCounter:Counter = new Counter
//  private val actorTimer:Timer = new Timer
//
//  metricsRegistry.register(s"counter-for-${actorName}", actorCounter)
//  metricsRegistry.register(s"timer-for-${actorName}", actorTimer)
}