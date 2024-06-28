package kamon.instrumentation.pekko.remote

import _root_.kanela.agent.api.instrumentation.InstrumentationBuilder
import kamon.instrumentation.context.{CaptureCurrentContextOnExit, HasContext, InvokeWithCapturedContext}

class MessageBufferInstrumentation extends InstrumentationBuilder {

  /**
    * Ensures that the Context traveling with outgoing messages will be properly propagated if those messages are
    * temporarily held on a MessageBuffer. This happens, for example, when sending messages to shard that has not yet
    * started.
    */
  onType("org.apache.pekko.util.MessageBuffer$Node")
    .mixin(classOf[HasContext.Mixin])
    .advise(isConstructor, classOf[CaptureCurrentContextOnExit])
    .advise(method("apply"), classOf[InvokeWithCapturedContext])

}
