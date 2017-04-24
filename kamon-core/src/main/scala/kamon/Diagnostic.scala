package kamon

// The types are just an idea, they will need further refinement.
trait Diagnostic {
  def isAspectJWorking: Boolean
  def detectedModules: Seq[String]
  def entityFilterPatterns: Seq[String]
  def metricSubscribers: Seq[String]
  def traceSubscribers: Seq[String]

  // Category Name => Count
  def entityCount: Map[String, Long]
}
