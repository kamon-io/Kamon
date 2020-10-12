package kamon.instrumentation.elasticsearch

object RequestNameConverter {
  def convert(className: String, fallback: String): String = {
    val requestType = Option(className).getOrElse(fallback)
    s"elasticsearch/$requestType"
  }
}
