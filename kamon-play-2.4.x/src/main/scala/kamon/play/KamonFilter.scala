/*
 * =========================================================================================
 * Copyright © 2013-2016 the kamon project <http://kamon.io/>
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

package kamon.play

import kamon.trace.Tracer
import kamon.util.SameThreadExecutionContext
import play.api.mvc.{ EssentialAction, EssentialFilter, Result }

class KamonFilter extends EssentialFilter {
  def apply(next: EssentialAction) = EssentialAction((requestHeader) ⇒ {
    def onResult(result: Result): Result = {
      Tracer.currentContext.collect { ctx ⇒
        ctx.finish()
        PlayExtension.httpServerMetrics.recordResponse(ctx.name, result.header.status.toString)

        if (PlayExtension.includeTraceToken) result.withHeaders(PlayExtension.traceTokenHeaderName -> ctx.token)
        else result

      } getOrElse result
    }
    //override the current trace name
    Tracer.currentContext.rename(PlayExtension.generateTraceName(requestHeader))
    // Invoke the action
    next(requestHeader).map(onResult)(SameThreadExecutionContext)
  })
}