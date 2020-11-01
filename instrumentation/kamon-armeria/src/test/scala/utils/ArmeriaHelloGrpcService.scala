package utils

import kamon.armeria.instrumentation.grpc.GrpcExample.{ArmeriaHelloServiceGrpc, HelloReply, HelloRequest}

import scala.concurrent.Future

class ArmeriaHelloGrpcService extends ArmeriaHelloServiceGrpc.ArmeriaHelloService {
  override def hello(request: HelloRequest): Future[HelloReply] = {
    Future.successful(HelloReply(s"Hello, ${request.name}!"))
  }
}
