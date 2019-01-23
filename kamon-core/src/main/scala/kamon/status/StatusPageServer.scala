package kamon.status

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.{Status => StatusCode}

class StatusPageServer(hostname: String, port: Int, resourceLoader: ClassLoader, status: Status)
    extends NanoHTTPD(hostname, port) {

  private val RootResourceDirectory = "status"
  private val ResourceExtensionRegex = ".*\\.([a-zA-Z]*)".r


  override def serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response = {
    if(session.getMethod() == NanoHTTPD.Method.GET) {
      if(session.getUri().startsWith("/status")) {

        // Serve the current status data on Json.
        session.getUri() match {
          case "/status/config"       => json(status.baseInfo())
          case "/status/modules"      => json(status.moduleRegistry())
          case _                      => NotFound
        }

      } else {
        // Serve resources from the status page folder.
        val resource = if (session.getUri() == "/") "/index.html" else session.getUri()
        val resourcePath = RootResourceDirectory + resource
        val resourceStream = resourceLoader.getResourceAsStream(resourcePath)

        if (resourceStream == null) NotFound else {
          NanoHTTPD.newChunkedResponse(StatusCode.OK, mimeType(resource), resourceStream)
        }
      }

    } else NotAllowed
  }

  private def mimeType(resource: String): String = {
    val ResourceExtensionRegex(resourceExtension) = resource
    resourceExtension match {
      case "css"  => "text/css"
      case "js"   => "application/javascript"
      case "ico"  => "image/x-icon"
      case "html" => "text/html"
      case _      => "text/plain"
    }
  }

  private def json[T : JsonMarshalling](instance: T): Response = {
    val builder = new java.lang.StringBuilder()
    implicitly[JsonMarshalling[T]].toJson(instance, builder)
    NanoHTTPD.newFixedLengthResponse(StatusCode.OK, "application/json", builder.toString())
  }

  private val NotAllowed = NanoHTTPD.newFixedLengthResponse(
    StatusCode.METHOD_NOT_ALLOWED,
    NanoHTTPD.MIME_PLAINTEXT,
    "Only GET requests are allowed.")

  private val NotFound = NanoHTTPD.newFixedLengthResponse(
    StatusCode.NOT_FOUND,
    NanoHTTPD.MIME_PLAINTEXT,
    "The requested resource was not found.")
}