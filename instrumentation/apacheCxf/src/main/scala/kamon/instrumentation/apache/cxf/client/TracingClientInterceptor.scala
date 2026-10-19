package kamon.instrumentation.apache.cxf.client

import kamon.Kamon
import kamon.context.Storage.Scope
import kamon.trace.Span
import org.apache.cxf.message.Message
import org.apache.cxf.phase.Phase.{POST_INVOKE, RECEIVE, SETUP}
import org.apache.cxf.phase.{PhaseInterceptor, PhaseInterceptorChain}

import java.util.Collections

private sealed abstract class AbstractTracingClientInterceptor(phase: String) extends PhaseInterceptor[Message] {

  protected val TRACE_SCOPE = "org.apache.cxf.tracing.client.kamon.traceScope"

  override def getAfter: java.util.Set[String] = Collections.emptySet()

  override def getBefore: java.util.Set[String] = Collections.emptySet()

  override def getId: String = getClass.getName

  override def getPhase: String = phase

  override def getAdditionalInterceptors: java.util.Collection[PhaseInterceptor[_ <: Message]] = null

  override def handleFault(message: Message): Unit = {}

  protected def isAsyncResponse: Boolean = !PhaseInterceptorChain.getCurrentMessage.getExchange.isSynchronous

  protected def processFailed(message: Message): Unit = {
    message.getExchange.get(TRACE_SCOPE) match {
      case holder: TraceScopeHolder =>
        if (holder != null) {
          holder.traceScope.foreach(t =>
            if (t.handler != null) {
              val exception = message.getContent(classOf[Exception])
              ClientProxyFactoryBeanInstrumentation.processResponse(t.handler, message, exception)
              t.close()
            }
          )
        }
    }
  }
}

private class TracingClientSetupInterceptor extends AbstractTracingClientInterceptor(SETUP) {

  override def handleMessage(message: Message): Unit = {
    val parentContext = Kamon.currentContext()
    val builder = ApacheCxfClientHelper.toRequestBuilder(message)
    val handler = ClientProxyFactoryBeanInstrumentation.cxfClientInstrumentation.createHandler(builder, parentContext)
    val scope = Kamon.storeContext(parentContext.withEntry(Span.Key, handler.span))

    val holder: TraceScopeHolder =
      // In case of asynchronous client invocation, the span should be detached as JAX-WS
      // client request / response interceptors are going to be executed in different threads.
      if (isAsyncResponse) new TraceScopeHolder(Option(TraceScope(handler, Option(scope))), true)
      else new TraceScopeHolder(Option(TraceScope(handler, Option(scope))))

    message.getExchange.put(TRACE_SCOPE, holder)
  }

  override def handleFault(message: Message): Unit = message.getExchange.get(TRACE_SCOPE) match {
    case holder: TraceScopeHolder =>
      if (holder != null) {
        holder.traceScope.foreach(t => {
          var newScope: Scope = null
          try {
            // If the client invocation was asynchronous, the trace span has been created
            // in another thread and should be re-attached to the current one.
            if (holder.detached) {
              newScope = t.scope.map(s => Kamon.storeContext(s.context.withEntry(Span.Key, t.handler.span))).orNull
            }
            val exception = message.getContent(classOf[Exception])
            ClientProxyFactoryBeanInstrumentation.processResponse(t.handler, message, exception)
          } finally {
            if (newScope != null) newScope.close()
            t.close()
          }
        })
      }
  }
}

private class TracingClientReceiveInterceptor extends AbstractTracingClientInterceptor(RECEIVE) {

  override def handleMessage(message: Message): Unit = message.getExchange.get(TRACE_SCOPE) match {
    case holder: TraceScopeHolder =>
      if (holder != null) {
        holder.traceScope.foreach(t =>
          // If the client invocation was asynchronous, the trace span has been created
          // in another thread and should be re-attached to the current one.
          if (holder.detached) {
            // Close scope which has been created in another thread
            t.scope.foreach(s => s.close())
            val newScope = t.scope.map(s => Kamon.storeContext(s.context.withEntry(Span.Key, t.handler.span)))
            val modifyHolder = new TraceScopeHolder(Option(TraceScope(t.handler, newScope)), holder.detached)
            message.getExchange.put(TRACE_SCOPE, modifyHolder)
          }
        )
      }
  }
  override def handleFault(message: Message): Unit = processFailed(message)
}

private class TracingClientPostInvokeInterceptor extends AbstractTracingClientInterceptor(POST_INVOKE) {

  override def handleMessage(message: Message): Unit = message.getExchange.get(TRACE_SCOPE) match {
    case holder: TraceScopeHolder =>
      if (holder != null) {
        holder.traceScope.foreach(t =>
          try {
            ClientProxyFactoryBeanInstrumentation.processResponse(t.handler, message)
          } finally {
            t.close()
          }
        )
      }
  }
  override def handleFault(message: Message): Unit = processFailed(message)
}
