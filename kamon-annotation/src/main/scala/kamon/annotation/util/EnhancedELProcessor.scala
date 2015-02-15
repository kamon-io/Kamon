package kamon.annotation.util

import javax.el.ELProcessor

import kamon.Kamon
import kamon.annotation.Annotation

import scala.util.{Failure, Success, Try}

/**
 * Pimp ELProcessor injecting some useful methods.
 */
object EnhancedELProcessor {
  val Pattern = """[#|$]\{(.*)\}""".r

  implicit class Syntax(val processor: ELProcessor) extends AnyVal {
    import scala.collection.JavaConverters._

    def evalToString(expression: String): String = {
      extract(expression) map (processor.eval(_).toString) getOrElse expression
    }

    def evalToMap(expression: String): Map[String, String] = {
      extract(expression) map { str =>
        Try(processor.eval(s"{$str}").asInstanceOf[java.util.HashMap[String, String]].asScala.toMap) match {
          case Success(value) => value
          case Failure(cause) =>
            println(cause.getMessage)
//            Kamon(Annotation).log.error(cause.getMessage)
            Map.empty[String,String]
        }
      } getOrElse Map.empty
    }
  }

  private def extract(expression: String): Option[String] = expression match {
    case Pattern(ex) ⇒ Some(ex)
    case _           ⇒ None
  }
}