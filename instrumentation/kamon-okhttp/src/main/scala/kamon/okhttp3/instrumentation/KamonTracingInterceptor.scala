/*
 * =========================================================================================
 * Copyright © 2013-2020 the kamon project <http://kamon.io/>
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

package kamon.okhttp3.instrumentation

import okhttp3.{Interceptor, Response}

final class KamonTracingInterceptor extends Interceptor {

  override def intercept(chain: Interceptor.Chain): Response = {
    val clientRequestHandler = KamonOkHttpTracing.withNewSpan(chain.request)
    val request = clientRequestHandler.request
    try {
      val response = chain.proceed(request)
      KamonOkHttpTracing.successContinuation(clientRequestHandler, response)
    } catch {
      case error: Throwable =>
        KamonOkHttpTracing.failureContinuation(clientRequestHandler, error)
        throw error
    }
  }
}
