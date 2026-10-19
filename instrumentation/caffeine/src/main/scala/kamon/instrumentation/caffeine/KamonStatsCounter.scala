package kamon.instrumentation.caffeine

import com.github.benmanes.caffeine.cache.RemovalCause
import com.github.benmanes.caffeine.cache.stats.{CacheStats, StatsCounter}
import kamon.Kamon

class KamonStatsCounter(name: String) extends StatsCounter {
  val hitsCounter = Kamon.counter(s"cache.${name}.hits").withoutTags()
  val missesCounter = Kamon.counter(s"cache.${name}.misses").withoutTags()
  val evictionCount = Kamon.counter(s"cache.${name}.evictions").withoutTags()
  val loadSuccessTime = Kamon.timer(s"cache.${name}.load-time.success").withoutTags()
  val loadFailureTime = Kamon.timer(s"cache.${name}.load-time.failure").withoutTags()
  val evictionWeight = Kamon.counter(s"cache.${name}.eviction.weight")
  val evictionWeightInstruments = RemovalCause.values()
    .map(cause => cause -> evictionWeight.withTag("eviction.cause", cause.name()))
    .toMap

  override def recordHits(count: Int): Unit = hitsCounter.increment(count)

  override def recordMisses(count: Int): Unit = missesCounter.increment(count)

  override def recordLoadSuccess(loadTime: Long): Unit = loadSuccessTime.record(loadTime)

  override def recordLoadFailure(loadTime: Long): Unit = loadFailureTime.record(loadTime)

  override def recordEviction(): Unit = {
    evictionCount.increment()
  }

  override def recordEviction(weight: Int): Unit = {
    evictionCount.increment()
    evictionWeight.withoutTags().increment(weight)
  }

  override def recordEviction(weight: Int, cause: RemovalCause): Unit = {
    evictionCount.increment()
    evictionWeightInstruments.get(cause).map(_.increment(weight))
  }

  /**
    * Overrides the snapshot method and returns stubbed CacheStats.
    * When using KamonStatsCounter, it is assumed that you are using a
    * reporter, and are not going to be printing or logging the stats.
    */
  override def snapshot() = new CacheStats(0, 0, 0, 0, 0, 0, 0)
}
