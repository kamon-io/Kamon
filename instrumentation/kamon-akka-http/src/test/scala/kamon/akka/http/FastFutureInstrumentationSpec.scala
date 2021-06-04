package kamon.akka.http

import java.util.concurrent.CountDownLatch

import akka.http.scaladsl.util.FastFuture
import kamon.instrumentation.context.HasContext
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future, Promise}
import kamon.instrumentation.futures.scala.ScalaFutureInstrumentation.trace
import akka.http.scaladsl.util.FastFuture.EnhancedFuture
import kamon.Kamon
import kamon.context.Context

import scala.util.Try

class FastFutureInstrumentationSpec extends WordSpec with Matchers {

  /**
    *  DEPRECATED
    *
    *  This spec is ignored since Kamon 2.2.0, along with the deprecation of the Future Chaining instrumentation, and
    *  should be completely removed before releasing Kamon 2.3.0.
    *
    *  We are keeping this spec only for the rare case that we might need to fix a bug on the Future Chaining
    *  instrumentation while we keep it in maintenance mode.
    */
  "the FastFuture instrumentation" ignore {
    "keep the Context captured by the Future from which it was created" when {
      "calling .map/.flatMap/.onComplete and the original Future has not completed yet" in {
        val completeSignal = new CountDownLatch(1)
        val future = trace("async-operation") {
          Future {
            completeSignal.await()
            "Hello World"
          }
        }

        val onCompleteFuture = Promise[Context]
        val fastFutures = Seq(
          future.fast.map(_ => Kamon.currentContext()),
          future.fast.flatMap(_ => Future(Kamon.currentContext())),
          future.fast.map(_ => "").flatMap(_ => Future(Kamon.currentContext())),
          future.fast.map(_ => "").map(_ => Kamon.currentContext()),
          future.fast.map(_ => { val c = Kamon.currentContext(); onCompleteFuture.complete(Try(c)); c }),
          onCompleteFuture.future
        )

        // When the future is finished, the Context stored on it should have the
        // Span for the async-operation above, but the current Thread should be clean.
        Kamon.currentContext() shouldBe empty
        completeSignal.countDown()
        Await.ready(future, 10 seconds)
        val fastFutureContexts = Await.result(FastFuture.sequence(fastFutures), 10 seconds)
        val futureContext = future.value.get.asInstanceOf[HasContext].context


        fastFutureContexts.foreach(context => context shouldBe futureContext)
      }

      "calling .map/.flatMap/.onComplete and the original Future has already completed" in {
        val completeSignal = new CountDownLatch(1)
        val future = trace("async-operation") {
          Future {
            completeSignal.await()
            "Hello World"
          }
        }

        // When the future is finished, the Context stored on it should have the
        // Span for the async-operation above, but the current Thread should be clean.
        Kamon.currentContext() shouldBe empty
        completeSignal.countDown()
        Await.ready(future, 10 seconds)
        val futureContext = future.value.get.asInstanceOf[HasContext].context

        val onCompleteFuture = Promise[Context]
        val fastFutures = Seq(
          future.fast.map(_ => Kamon.currentContext()),
          future.fast.flatMap(_ => Future(Kamon.currentContext())),
          future.fast.map(_ => "").flatMap(_ => Future(Kamon.currentContext())),
          future.fast.map(_ => "").map(_ => Kamon.currentContext()),
          future.fast.map(_ => { val c = Kamon.currentContext(); onCompleteFuture.complete(Try(c)); c }),
          onCompleteFuture.future
        )

        val fastFutureContexts = Await.result(FastFuture.sequence(fastFutures), 10 seconds)
        fastFutureContexts.foreach(context => context shouldBe futureContext)
      }
    }
  }
}
