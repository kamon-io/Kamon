/*
 *  ==========================================================================================
 *  Copyright © 2013-2022 The Kamon Project <https://kamon.io/>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 *  except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the
 *  License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied. See the License for the specific language governing permissions
 *  and limitations under the License.
 *  ==========================================================================================
 */

package kamon.instrumentation.aws.sdk

import com.amazonaws.{Request, Response}
import com.amazonaws.handlers.{HandlerContextKey, RequestHandler2}
import kamon.Kamon
import kamon.trace.Span

/**
  * RequestHandler for the AWS Java SDK version 1.x
  *
  * Bare-bones request handler that creates Spans for all client requests made with the AWS SDK. There is no need to
  * add this interceptor by hand anywhere, the AWS SDK will pick it up automatically from the classpath because it is
  * included in the "software/amazon/awssdk/global/handlers/execution.interceptors" file shipped with this module.
  */
class AwsSdkRequestHandler extends RequestHandler2 {
  import AwsSdkRequestHandler.SpanContextKey

  override def beforeRequest(request: Request[_]): Unit = {
    if(Kamon.enabled()) {
      val serviceName = request.getServiceName
      val originalRequestName = {
        // Remove the "Request" part of the request class name, if present
        var requestClassName = request.getOriginalRequest.getClass.getSimpleName
        if (requestClassName.endsWith("Request"))
          requestClassName = requestClassName.substring(0, requestClassName.length - 7)
        requestClassName
      }

      val operationName = serviceName + "." + originalRequestName

      val clientSpan = serviceName match {
        case "AmazonSQS" => Kamon.producerSpanBuilder(operationName, serviceName).start()
        case _ => Kamon.clientSpanBuilder(operationName, serviceName).start()
      }

      request.addHandlerContext(SpanContextKey, clientSpan)
    }
  }

  override def afterResponse(request: Request[_], response: Response[_]): Unit = {
    if(Kamon.enabled()) {
      val requestSpan = request.getHandlerContext(SpanContextKey)
      if (requestSpan != null) {
        requestSpan.finish()
      }
    }
  }

  override def afterError(request: Request[_], response: Response[_], e: Exception): Unit = {
    if(Kamon.enabled()) {
      val requestSpan = request.getHandlerContext(SpanContextKey)
      if (requestSpan != null) {
        requestSpan
          .fail(e)
          .finish()
      }
    }
  }
}

object AwsSdkRequestHandler {
  private val SpanContextKey = new HandlerContextKey[Span](classOf[Span].getName)
}