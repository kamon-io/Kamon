package kamon.instrumentation.akka.grpc

import kamon.Kamon
import kamon.context.Storage
import kamon.util.CallingThreadExecutionContext
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice

import scala.concurrent.Future
import scala.util.{Failure, Success}


class AkkaGrpcClientInstrumentation[I] extends InstrumentationBuilder {
  onType("akka.grpc.internal.ScalaUnaryRequestBuilder")
    .advise(method("invoke"), classOf[AkkaGrpcClientInstrumentationAdvise])

  onType("akka.grpc.internal.MetadataImpl").advise(method("toGoogleGrpcMetadata"), TestXXX)
}

object AkkaGrpcClientInstrumentation {
  def onExit[I, R](@Advice.Argument(0) request: I, @Advice.Local("scope") scope: Storage.Scope, @Advice.Return responseFuture: Future[R]): Future[R] = {
    responseFuture.onComplete {
      case Success(r) => {
        Kamon.currentSpan().finish()
      }
      case Failure(t) => Kamon.currentSpan().fail(t).finish()
    }(CallingThreadExecutionContext)
    scope.close()
    responseFuture
  }
}

object TestXXX {
  @Advice.OnMethodExit
  def onExit[I, R](@Advice.Return metaData: io.grpc.Metadata): io.grpc.Metadata = {
    //add traceId
    metaData.put(io.grpc.Metadata.Key.of("traceid", io.grpc.Metadata.ASCII_STRING_MARSHALLER), Kamon.currentSpan().trace.id.toString)
    metaData
  }
}
