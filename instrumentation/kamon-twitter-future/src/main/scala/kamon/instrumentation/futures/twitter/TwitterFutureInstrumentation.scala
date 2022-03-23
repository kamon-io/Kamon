package kamon
package instrumentation
package futures
package twitter

import kamon.instrumentation.context.{CaptureCurrentContextOnExit, HasContext, InvokeWithCapturedContext}
import kanela.agent.api.instrumentation.InstrumentationBuilder

class TwitterFutureInstrumentation extends InstrumentationBuilder {

  onType("com.twitter.util.Promise$WaitQueue")
    .mixin(classOf[HasContext.Mixin])
    .advise(isConstructor, CaptureCurrentContextOnExit)
    .advise(method("runInScheduler"), InvokeWithCapturedContext)
}
