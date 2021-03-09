package kamon.instrumentation.spring.client

import kanela.agent.api.instrumentation.InstrumentationBuilder

class SpringWebClientInstrumentation extends InstrumentationBuilder {
  /*
   * Instruments spring WebClient, mutating the request and registering callbacks.
   */
  onType("org.springframework.web.reactive.function.client.ExchangeFunctions$DefaultExchangeFunction")
    .advise(method("exchange"), classOf[ExchangeAdvice])
}
