package kamon.instrumentation.spring.client

import kanela.agent.api.instrumentation.InstrumentationBuilder

class SpringWebClientInstrumentation extends InstrumentationBuilder {
  /*
   * Instruments spring WebClient, mutating the request and registering callbacks.
   */
  onSubTypesOf("org.springframework.web.reactive.function.client.ExchangeFunction")
    .advise(method("exchange"), classOf[ExchangeAdvice])
}
