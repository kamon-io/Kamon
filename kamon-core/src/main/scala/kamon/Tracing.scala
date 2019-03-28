package kamon

import kamon.trace.{IdentityProvider, Tracer}

trait Tracing { self: Configuration with ClassLoading with Utilities =>
  private val _tracer = new Tracer.Default(Kamon, config(), clock(), this, this)
  onReconfigure(newConfig => _tracer.reconfigure(newConfig))

  def buildSpan(operationName: String): Tracer.SpanBuilder =
    _tracer.buildSpan(operationName)

  def identityProvider: IdentityProvider =
    _tracer.identityProvider

  protected def tracer(): Tracer.Default =
    _tracer

}
