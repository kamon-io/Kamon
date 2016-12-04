package kamon.trace.logging

import kamon.testkit.BaseKamonSpec
import kamon.trace.{EmptyTraceContext, Tracer}
import org.slf4j.MDC

class MdcKeysSupportSpec extends BaseKamonSpec("mdc-keys-support-spec") {

  "Running code with MDC support" should {
    "add nothing to the MDC" when {
      "the trace context is empty" in {
        // Given an empty trace context.
        Tracer.withContext(EmptyTraceContext) {
          // When some code is executed with MDC support.
          MdcKeysSupport.withMdc {
            // Then the MDC should not contain the trace token.
            Option(MDC.get(MdcKeysSupport.traceTokenKey)) should be(None)
            // Or name
            Option(MDC.get(MdcKeysSupport.traceNameKey)) should be(None)
          }
        }
      }
    }
    "add the trace token and name to the context" when {
      "the trace context is not empty" in {
        // Given a trace context.
        Tracer.withNewContext("name", Some("token")) {
          // When some code is executed with MDC support.
          MdcKeysSupport.withMdc {
            // Then the MDC should contain the trace token.
            Option(MDC.get(MdcKeysSupport.traceTokenKey)) should be(Some("token"))
            // And name
            Option(MDC.get(MdcKeysSupport.traceNameKey)) should be(Some("name"))
          }

          // Then after code is executed the MDC should have been cleared.
          Option(MDC.get(MdcKeysSupport.traceTokenKey)) should be(None)
          Option(MDC.get(MdcKeysSupport.traceNameKey)) should be(None)
        }
      }
    }
  }

}
