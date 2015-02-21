package kamon.annotation.el

import javax.el.ELProcessor

import kamon.Kamon
import kamon.annotation.Annotation

import scala.util.{ Failure, Success, Try }

/**
 * Pimp ELProcessor injecting some useful methods.
 */
object EnhancedELProcessor {
  private val Pattern = """[#|$]\{(.*)\}""".r

  implicit class Syntax(val processor: ELProcessor) extends AnyVal {
    import scala.collection.JavaConverters._

    def evalToString(expression: String): String = extract(expression).map { str ⇒
      eval[String](str) match {
        case Success(value) ⇒ value
        case Failure(cause) ⇒
          Kamon(Annotation).log.error(s"${cause.getMessage} -> we will complete the operation with 'unknown' string")
          "unknown"
      }
    } getOrElse expression

    def evalToMap(expression: String): Map[String, String] = extract(expression).map { str ⇒
      eval[Map[String, String]](s"{$str}") match {
        case Success(value) ⇒ value.asInstanceOf[java.util.HashMap[String, String]].asScala.toMap
        case Failure(cause) ⇒
          Kamon(Annotation).log.error(s"${cause.getMessage} -> we will complete the operation with an empty map")
          Map.empty[String, String]
      }
    } getOrElse Map.empty[String, String]

    private def eval[A](str: String): Try[A] = Try(processor.eval(str).asInstanceOf[A])

    private def extract(expression: String): Option[String] = expression match {
      case Pattern(ex) ⇒ Some(ex)
      case _           ⇒ None
    }
  }
}
