package kamon.instrumentation.play

import play.api.routing.HandlerDef

import scala.collection.concurrent.TrieMap

trait RouterOperationNameGenerator {
  def generateOperationName(handlerDef: HandlerDef): String
}

class DefaultRouterOperationNameGenerator extends RouterOperationNameGenerator {

  private val _operationNameCache = TrieMap.empty[String, String]
  private val _normalizePattern = """\$([^<]+)<[^>]+>""".r

  def generateOperationName(handlerDef: HandlerDef): String = {
    _operationNameCache.getOrElseUpdate(
      handlerDef.path, {
        // Convert paths of form /foo/bar/$paramname<regexp>/blah to /foo/bar/paramname/blah
        _normalizePattern.replaceAllIn(handlerDef.path, "$1")
      }
    )
  }

}
