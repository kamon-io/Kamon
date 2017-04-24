package kamon.trace

trait Tracer extends io.opentracing.Tracer {
  def sampler: Sampler
}
