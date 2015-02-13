package kamon.annotation.util

import javax.el.ELProcessor

import scala.collection.mutable

/**
 * Pimp ELProcessor injecting some useful methods.
 */
object EnhancedELProcessor {
  val Pattern = """[#|$]\{(.*)\}""".r

  implicit class Syntax(val processor: ELProcessor) extends AnyVal {
    import scala.collection.JavaConverters._

    def evalToString(expression: String): String = extract(expression) map (processor.eval(_).toString) getOrElse expression

    def evalToMap(expression: String): mutable.Map[String, String] = {
      extract(expression) map (processor.eval(_).asInstanceOf[java.util.Map[String, String]].asScala) getOrElse mutable.Map.empty
    }
  }

  private def extract(expression: String): Option[String] = expression match {
    case Pattern(ex) ⇒ Some(ex)
    case _           ⇒ None
  }
}