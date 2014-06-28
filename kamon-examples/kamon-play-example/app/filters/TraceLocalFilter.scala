package filters

import kamon.trace.{TraceRecorder, TraceLocal}
import play.api.Logger
import play.api.mvc.{Result, RequestHeader, Filter}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future

object TraceLocalKey extends TraceLocal.TraceLocalKey {
  type ValueType = String
}

/*
 By default Kamon spreads the trace-token-header-name but sometimes is necessary pass through the application requests with some infomation like
 extra headers, with kamon it's possible using TraceLocalStorage, in Play applications we can do an Action Filter or using Action Composition,
 in this example we are using a simple filter where given a Header store the value and then put the value in the result headers..

 More detailed used of TraceLocalStorage: https://github.com/kamon-io/Kamon/blob/b17539d231da923ea854c01d2c69eb02ef1e85b1/kamon-core/src/test/scala/kamon/trace/TraceLocalSpec.scala
 */
object TraceLocalFilter extends Filter {
  val logger = Logger(this.getClass)
  val TraceLocalStorageKey = "MyTraceLocalStorageKey"

  override def apply(next: (RequestHeader) ⇒ Future[Result])(header: RequestHeader): Future[Result] = {
    TraceRecorder.withTraceContext(TraceRecorder.currentContext) {

      TraceLocal.store(TraceLocalKey)(header.headers.get(TraceLocalStorageKey).getOrElse("unknown"))

      next(header).map {
        val traceTokenValue = TraceLocal.retrieve(TraceLocalKey).getOrElse("unknown")
        logger.info(s"traceTokenValue: $traceTokenValue")
        result ⇒ result.withHeaders((TraceLocalStorageKey -> traceTokenValue))
      }
    }
  }
}
