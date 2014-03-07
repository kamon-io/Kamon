/*
 * =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
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

package kamon.metrics

import org.scalatest.{ Matchers, WordSpec }
import kamon.metrics.MetricSnapshot.Measurement

class MetricSnapshotSpec extends WordSpec with Matchers {

  "a metric snapshot" should {
    "support a max operation" in new SnapshotFixtures {
      snapshotA.max should be(17)
      snapshotB.max should be(10)
    }

    "support a min operation" in new SnapshotFixtures {
      snapshotA.min should be(1)
      snapshotB.min should be(2)
    }

    "be able to merge with other snapshot" in new SnapshotFixtures {
      val merged = snapshotA.merge(snapshotB)

      merged.min should be(1)
      merged.max should be(17)
      merged.numberOfMeasurements should be(200)
      merged.measurements.map(_.value) should contain inOrderOnly (1, 2, 4, 5, 7, 10, 17)
    }

    "be able to merge with empty snapshots" in new SnapshotFixtures {
      snapshotA.merge(emptySnapshot) should be(snapshotA)
      emptySnapshot.merge(snapshotA).merge(emptySnapshot) should be(snapshotA)
    }

  }

  trait SnapshotFixtures {
    val emptySnapshot = MetricSnapshot(0, Scale.Unit, Vector.empty)

    val snapshotA = MetricSnapshot(100, Scale.Unit, Vector(
      Measurement(1, 3),
      Measurement(2, 15),
      Measurement(5, 68),
      Measurement(7, 13),
      Measurement(17, 1)))

    val snapshotB = MetricSnapshot(100, Scale.Unit, Vector(
      Measurement(2, 6),
      Measurement(4, 48),
      Measurement(5, 39),
      Measurement(10, 7)))
  }
}
