/* ===================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */

package kamon.trace

import kamon.trace.Trace.SegmentCompletionHandle

object Segments {

  trait Category
  case object HttpClientRequest extends Category

  case class Start(category: Category, description: String = "",
                   attributes: Map[String, String] = Map(), timestamp: Long = System.nanoTime())

  case class End(attributes: Map[String, String] = Map(), timestamp: Long = System.nanoTime())

  case class Segment(start: Start, end: End)

  trait SegmentCompletionHandleAware {
    var completionHandle: Option[SegmentCompletionHandle]
  }

  trait ContextAndSegmentCompletionAware extends ContextAware with SegmentCompletionHandleAware
}
