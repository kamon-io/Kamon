package kamon.instrumentation

import org.aspectj.lang.annotation.{After, Pointcut, Aspect}
import kamon.Tracer
import kamon.trace.UowTracing.{Finish, Rename}
import spray.http.HttpRequest
import spray.can.server.OpenRequestComponent

@Aspect
class SprayServerInstrumentation {

  @Pointcut("execution(spray.can.server.OpenRequestComponent$DefaultOpenRequest.new(..)) && args(enclosing, request, closeAfterResponseCompletion, timestamp)")
  def openRequestInit(enclosing: OpenRequestComponent, request: HttpRequest, closeAfterResponseCompletion: Boolean, timestamp: Long): Unit = {}

  @After("openRequestInit(enclosing, request, closeAfterResponseCompletion, timestamp)")
  def afterInit(enclosing: OpenRequestComponent, request: HttpRequest, closeAfterResponseCompletion: Boolean, timestamp: Long): Unit = {
  //@After("openRequestInit()")
  //def afterInit(): Unit = {
    Tracer.start
    println("Created the context: " + Tracer.context() + " for the transaction: " + request.uri.path.toString())
    Tracer.context().map(_.entries ! Rename(request.uri.path.toString()))
  }

  @Pointcut("execution(* spray.can.server.OpenRequest.handleResponseEndAndReturnNextOpenRequest(..))")
  def openRequestCreation(): Unit = {}

  @After("openRequestCreation()")
  def afterFinishingRequest(): Unit = {
    println("Finishing a request: " + Tracer.context())

    Tracer.context().map(_.entries ! Finish())
  }
}