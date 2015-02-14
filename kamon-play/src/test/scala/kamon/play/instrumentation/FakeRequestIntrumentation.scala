/* ===================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

package kamon.play.instrumentation

import org.aspectj.lang.annotation.{ DeclareMixin, Aspect }
import kamon.trace.TraceContextAware

@Aspect
class FakeRequestIntrumentation {

  @DeclareMixin("play.api.test.FakeRequest")
  def mixinContextAwareNewRequest: TraceContextAware = TraceContextAware.default
}
