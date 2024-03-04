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

import kamon.Kamon
import kamon.trace.Span
import software.amazon.awssdk.core.interceptor.{
  Context,
  ExecutionAttribute,
  ExecutionAttributes,
  ExecutionInterceptor,
  SdkExecutionAttribute
}

/**
  * Execution Interceptor for the AWS Java SDK Version 2.x
  *
  * Bare-bones interceptor that creates Spans for all client requests made with the AWS SDK. There is no need to
  * add this interceptor by hand anywhere, the AWS SDK will pick it up automatically from the classpath because it is
  * included in the "software/amazon/awssdk/global/handlers/execution.interceptors" file shipped with this module.
  */
class AwsSdkClientExecutionInterceptor extends ExecutionInterceptor {
  import AwsSdkClientExecutionInterceptor.ClientSpanAttribute

  override def afterMarshalling(context: Context.AfterMarshalling, executionAttributes: ExecutionAttributes): Unit = {
    if (Kamon.enabled()) {
      val operationName = executionAttributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME)
      val serviceName = executionAttributes.getAttribute(SdkExecutionAttribute.SERVICE_NAME)
      val clientType = executionAttributes.getAttribute(SdkExecutionAttribute.CLIENT_TYPE)

      val clientSpan = Kamon.clientSpanBuilder(operationName, serviceName)
        .tag("aws.sdk.client_type", clientType.name())
        .start()

      executionAttributes.putAttribute(ClientSpanAttribute, clientSpan)
    }
  }

  override def afterExecution(context: Context.AfterExecution, executionAttributes: ExecutionAttributes): Unit = {
    if (Kamon.enabled()) {
      val kamonSpan = executionAttributes.getAttribute(ClientSpanAttribute)
      if (kamonSpan != null) {
        kamonSpan.finish()
      }
    }
  }

  override def onExecutionFailure(context: Context.FailedExecution, executionAttributes: ExecutionAttributes): Unit = {
    if (Kamon.enabled()) {
      val kamonSpan = executionAttributes.getAttribute(ClientSpanAttribute)
      if (kamonSpan != null) {
        kamonSpan.fail(context.exception()).finish()
      }
    }
  }
}

object AwsSdkClientExecutionInterceptor {
  private val ClientSpanAttribute = new ExecutionAttribute[Span]("SdkClientSpan")
}
