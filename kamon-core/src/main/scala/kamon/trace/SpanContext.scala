/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon.trace

import kamon.trace.IdentityProvider.Identifier
import kamon.trace.SpanContext.SamplingDecision

/**
  *
  * @param traceID
  * @param spanID
  * @param parentID
  * @param samplingDecision
  */
case class SpanContext(traceID: Identifier, spanID: Identifier, parentID: Identifier, samplingDecision: SamplingDecision) {

  def createChild(childSpanID: Identifier, samplingDecision: SamplingDecision): SpanContext =
    this.copy(parentID = this.spanID, spanID = childSpanID)
}

object SpanContext {

  val EmptySpanContext = SpanContext(
    traceID   = IdentityProvider.NoIdentifier,
    spanID    = IdentityProvider.NoIdentifier,
    parentID  = IdentityProvider.NoIdentifier,
    samplingDecision = SamplingDecision.DoNotSample
  )


  sealed trait SamplingDecision
  object SamplingDecision {

    /**
      *   The Trace is sampled, all child Spans should be sampled as well.
      */
    case object Sample extends SamplingDecision

    /**
      *   The Trace is not sampled, none of the child Spans should be sampled.
      */
    case object DoNotSample extends SamplingDecision

    /**
      *   The sampling decision has not been taken yet, the Tracer is free to decide when creating a Span.
      */
    case object Unknown extends SamplingDecision
  }

  /**
    *
    */

  sealed trait Baggage {
    def add(key: String, value:String): Unit
    def get(key: String): Option[String]
    def getAll(): Map[String, String]
  }

  object Baggage {
    def apply(): Baggage = new DefaultBaggage()

    case object EmptyBaggage extends Baggage {
      override def add(key: String, value: String): Unit = {}
      override def get(key: String): Option[String] = None
      override def getAll: Map[String, String] = Map.empty
    }


    final class DefaultBaggage extends Baggage {
      private var baggage: Map[String, String] = Map.empty

      def add(key: String, value: String): Unit = synchronized {
        baggage = baggage + (key -> value)
      }

      def get(key: String): Option[String] =
        baggage.get(key)

      def getAll: Map[String, String] =
        baggage
    }
  }
}
