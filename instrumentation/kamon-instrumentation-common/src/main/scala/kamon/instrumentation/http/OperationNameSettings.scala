package kamon.instrumentation.http

import kamon.util.Filter
import org.slf4j.LoggerFactory

import scala.util.Try

final case class OperationNameSettings(
    defaultOperationName: String,
    operationMappings: Map[Filter.Glob, String],
    operationNameGenerator: HttpOperationNameGenerator) {

  private val logger = LoggerFactory.getLogger(classOf[OperationNameSettings])

  private[http] def operationName(request: HttpMessage.Request): String = {
    Try {
      val requestPath = request.path

      //first apply any mappings rules
      val customMapping = operationMappings.collectFirst {
        case (pattern, operationName) if pattern.accept(requestPath) => operationName
      } orElse {
        //fallback to use any configured name generator
        operationNameGenerator.name(request)
      }

      customMapping.getOrElse(defaultOperationName)
    } getOrElse(defaultOperationName)
  }
}
