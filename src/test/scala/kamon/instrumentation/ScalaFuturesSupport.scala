package kamon.instrumentation

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}
import org.scalatest.concurrent.Futures
import java.util.concurrent.TimeUnit

trait ScalaFuturesSupport extends Futures {
  implicit def scalaFutureToFutureConcept[T](future: Future[T]): FutureConcept[T] = new FutureConcept[T] {
    def eitherValue: Option[Either[Throwable, T]] = {
      if(!future.isCompleted)
        None
      else
        future.value match {
          case None => None
          case Some(t) => t match {
            case Success(v) => Some(Right(v))
            case Failure(e) => Some(Left(e))
          }
        }
    }

    def isExpired: Boolean = false   // Scala futures cant expire

    def isCanceled: Boolean = false  // Scala futures cannot be cancelled

    override def futureValue(implicit config: PatienceConfig): T = {
      Await.result(future, Duration(config.timeout.totalNanos, TimeUnit.NANOSECONDS))
    }
  }
}
