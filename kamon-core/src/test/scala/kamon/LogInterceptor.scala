package kamon

//import uk.org.lidalia.slf4jext.Level
//import uk.org.lidalia.slf4jtest.{LoggingEvent, TestLogger}
//
//trait LogInterceptor {
//
//  def interceptLog[T](level: Level)(code: => T)(implicit tl: TestLogger): Seq[LoggingEvent] = {
//    import scala.collection.JavaConverters._
//    tl.clear()
//    val run = code
//    tl.getLoggingEvents().asScala.filter(_.getLevel == level)
//  }
//}
