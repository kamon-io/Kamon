/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.metric.instrument

import akka.actor.{ Scheduler, Cancellable }
import org.HdrHistogram.WriterReaderPhaser

import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

trait RefreshScheduler {
  def schedule(interval: FiniteDuration, refresh: () ⇒ Unit): Cancellable
}

/**
 *  Default implementation of RefreshScheduler that simply uses an [[akka.actor.Scheduler]] to schedule tasks to be run
 *  in the provided ExecutionContext.
 */
class DefaultRefreshScheduler(scheduler: Scheduler, dispatcher: ExecutionContext) extends RefreshScheduler {
  def schedule(interval: FiniteDuration, refresh: () ⇒ Unit): Cancellable =
    scheduler.schedule(interval, interval)(refresh.apply())(dispatcher)
}

object DefaultRefreshScheduler {
  def apply(scheduler: Scheduler, dispatcher: ExecutionContext): RefreshScheduler =
    new DefaultRefreshScheduler(scheduler, dispatcher)

  def create(scheduler: Scheduler, dispatcher: ExecutionContext): RefreshScheduler =
    apply(scheduler, dispatcher)
}

/**
 *  RefreshScheduler implementation that accumulates all the scheduled actions until it is pointed to another refresh
 *  scheduler. Once it is pointed, all subsequent calls to `schedule` will immediately be scheduled in the pointed
 *  scheduler.
 */
class LazyRefreshScheduler extends RefreshScheduler {
  private val _schedulerPhaser = new WriterReaderPhaser
  private val _backlog = new TrieMap[(FiniteDuration, () ⇒ Unit), RepointableCancellable]()
  @volatile private var _target: Option[RefreshScheduler] = None

  def schedule(interval: FiniteDuration, refresh: () ⇒ Unit): Cancellable = {
    val criticalEnter = _schedulerPhaser.writerCriticalSectionEnter()
    try {
      _target.map { scheduler ⇒
        scheduler.schedule(interval, refresh)

      } getOrElse {
        val entry = (interval, refresh)
        val cancellable = new RepointableCancellable(entry)

        _backlog.put(entry, cancellable)
        cancellable
      }

    } finally {
      _schedulerPhaser.writerCriticalSectionExit(criticalEnter)
    }
  }

  def point(target: RefreshScheduler): Unit = try {
    _schedulerPhaser.readerLock()

    if (_target.isEmpty) {
      _target = Some(target)
      _schedulerPhaser.flipPhase(10000L)
      _backlog.dropWhile {
        case ((interval, refresh), repointableCancellable) ⇒
          repointableCancellable.point(target.schedule(interval, refresh))
          true
      }
    } else sys.error("A LazyRefreshScheduler cannot be pointed more than once.")
  } finally { _schedulerPhaser.readerUnlock() }

  class RepointableCancellable(entry: (FiniteDuration, () ⇒ Unit)) extends Cancellable {
    private var _isCancelled = false
    private var _cancellable: Option[Cancellable] = None

    def isCancelled: Boolean = synchronized {
      _cancellable.map(_.isCancelled).getOrElse(_isCancelled)
    }

    def cancel(): Boolean = synchronized {
      _isCancelled = true
      _cancellable.map(_.cancel()).getOrElse(_backlog.remove(entry).nonEmpty)
    }

    def point(cancellable: Cancellable): Unit = synchronized {
      if (_cancellable.isEmpty) {
        _cancellable = Some(cancellable)

        if (_isCancelled)
          cancellable.cancel()

      } else sys.error("A RepointableCancellable cannot be pointed more than once.")

    }
  }
}

