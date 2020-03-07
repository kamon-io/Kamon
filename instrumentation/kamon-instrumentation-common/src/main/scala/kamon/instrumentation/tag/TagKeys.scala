package kamon
package instrumentation
package tag

/**
  * Contains a collection of tag keys usually used through several instrumentation modules.
  */
object TagKeys {

  val Port = "port"
  val Interface = "interface"
  val Component = "component"
  val HttpStatusCode = "http.status_code"
  val HttpUrl = "http.url"
  val HttpMethod = "http.method"

}
