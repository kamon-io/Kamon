package kamon
package trace

/**
  * Holds information shared across all Spans from the same Trace. It might seem like too little information but all in
  * all, a trace is just a bunch of Spans that share the same trace identifier ;).
  */
case class Trace (
  id: Identifier,
  samplingDecision: Trace.SamplingDecision
) {

  override def toString(): String = {
    s"{id=${id.string},samplingDecision=${samplingDecision}"
  }
}

object Trace {

  /**
    * A trace without identifier nor sampling decision. Used to signal that there is no trace information available.
    */
  val Empty = Trace(Identifier.Empty, SamplingDecision.Unknown)


  /**
    * A Sampling decision indicates whether Spans belonging to a trace should be captured and sent to the SpanReporters
    * or not.
    */
  sealed abstract class SamplingDecision
  object SamplingDecision {

    /**
      * Indicates that all Spans that belong to a trace should be captured and reported.
      */
    case object Sample extends SamplingDecision


    /**
      * Indicates that all Spans that belong to a trace should not be captured nor reported. Note that traces with a
      * "do not sample" decision will still generate Spans that can gather metrics and propagate with the Context, they
      * just don't get sent to the Span reporters.
      */
    case object DoNotSample extends SamplingDecision


    /**
      * Indicates that a sampling decision has not been made yet.
      */
    case object Unknown extends SamplingDecision
  }
}
