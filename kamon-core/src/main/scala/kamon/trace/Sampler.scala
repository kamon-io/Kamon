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

package kamon
package trace

/**
  * A sampler takes the decision of whether to collect and report Spans for a particular trace. Samplers are used only
  * when starting a new Trace.
  */
trait Sampler {

  /**
    * Decides whether a trace should be sampled or not. The provided SpanBuilder contains the information that has been
    * gathered so far for what will become the root Span for the new Trace.
    */
  def decide(rootSpanBuilder: SpanBuilder): Trace.SamplingDecision
}