package spraytest
/*
import akka.actor.ActorSystem
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Try, Success}
import kamon.actor.TransactionContext

object FutureTesting extends App {

  val actorSystem = ActorSystem("future-testing")
  implicit val ec = actorSystem.dispatcher
  implicit val tctx = TransactionContext(11, Nil)

  threadPrintln("In the initial Thread")


  val f = TraceableFuture {
    threadPrintln(s"Processing the Future, and the current context is: ${TransactionContext.current.get()}")
  }

  f.onComplete({
    case Success(a) => threadPrintln(s"Processing the first callback, and the current context is: ${TransactionContext.current.get()}")
  })

  f.onComplete({
    case Success(a) => threadPrintln(s"Processing the second callback, and the current context is: ${TransactionContext.current.get()}")
  })








  def threadPrintln(message: String) = println(s"Thread[${Thread.currentThread.getName}] says: [${message}]")

}




trait TransactionContextWrapper {
  def wrap[In, Out](f: => In => Out, tranContext: TransactionContext) = {
    TransactionContext.current.set(tranContext.fork)
    println(s"SetContext to: ${tranContext}")
    val result = f

    TransactionContext.current.remove()
    result
  }

}

class TraceableFuture[T](val future: Future[T]) extends TransactionContextWrapper {
  def onComplete[U](func: Try[T] => U)(implicit transactionContext: TransactionContext, executor: ExecutionContext): Unit = {
    future.onComplete(wrap(func, transactionContext))
  }
}

object TraceableFuture {

  implicit def toRegularFuture[T](tf: TraceableFuture[T]) = tf.future

  def apply[T](body: => T)(implicit transactionContext: TransactionContext, executor: ExecutionContext) = {
    val wrappedBody = contextSwitchWrapper(body, TransactionContext(transactionContext.dispatcherName, Nil))

    new TraceableFuture(Future { wrappedBody })
  }




  def contextSwitchWrapper[T](body: => T, transactionContext: TransactionContext) = {
    TransactionContext.current.set(transactionContext)
    val result = body
    TransactionContext.current.remove()
    result
  }
}*/

