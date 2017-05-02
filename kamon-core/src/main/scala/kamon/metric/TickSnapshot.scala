package kamon.metric

import java.time.Instant

case class TickSnapshot(interval: Interval, entities: Seq[EntitySnapshot])
case class Interval(from: Instant, to: Instant)



