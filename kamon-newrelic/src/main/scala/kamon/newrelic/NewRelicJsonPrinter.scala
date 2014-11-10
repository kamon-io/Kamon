package kamon.newrelic

import java.lang.{ StringBuilder ⇒ JStringBuilder }
import spray.json._
import scala.annotation.tailrec

// We need a special treatment of / that needs to be escaped as \/ for New Relic to work properly with all metrics.
// Without this custom json printer the scoped metrics are not displayed in the site.

// format: OFF
trait NewRelicJsonPrinter extends CompactPrinter {

  override def printString(s: String, sb: JStringBuilder) {
    import NewRelicJsonPrinter._
    @tailrec def firstToBeEncoded(ix: Int = 0): Int =
      if (ix == s.length) -1 else if (requiresEncoding(s.charAt(ix))) ix else firstToBeEncoded(ix + 1)

    sb.append('"')
    firstToBeEncoded() match {
      case -1 ⇒ sb.append(s)
      case first ⇒
        sb.append(s, 0, first)
        @tailrec def append(ix: Int): Unit =
          if (ix < s.length) {
            s.charAt(ix) match {
              case c if !requiresEncoding(c) => sb.append(c)
              case '"' => sb.append("\\\"")
              case '\\' => sb.append("\\\\")
              case '/'  => sb.append("\\/")
              case '\b' => sb.append("\\b")
              case '\f' => sb.append("\\f")
              case '\n' => sb.append("\\n")
              case '\r' => sb.append("\\r")
              case '\t' => sb.append("\\t")
              case x if x <= 0xF => sb.append("\\u000").append(Integer.toHexString(x))
              case x if x <= 0xFF => sb.append("\\u00").append(Integer.toHexString(x))
              case x if x <= 0xFFF => sb.append("\\u0").append(Integer.toHexString(x))
              case x => sb.append("\\u").append(Integer.toHexString(x))
            }
            append(ix + 1)
          }
        append(first)
    }
    sb.append('"')
  }
}

object NewRelicJsonPrinter extends NewRelicJsonPrinter {

  def requiresEncoding(c: Char): Boolean =
  // from RFC 4627
  // unescaped = %x20-21 / %x23-5B / %x5D-10FFFF
    c match {
      case '"'  ⇒ true
      case '\\' ⇒ true
      case '/'  ⇒ true
      case c    ⇒ c < 0x20
    }
}