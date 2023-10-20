/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon.instrumentation.pekko.grpc

import scala.concurrent.Future
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source


class GreeterServiceImpl(implicit mat: Materializer) extends GreeterService {
  import mat.executionContext

  override def sayHello(in: HelloRequest): Future[HelloReply] = {
    Future.successful(HelloReply(s"Hello, ${in.name}"))
  }

  override def itKeepsTalking(in: Source[HelloRequest, NotUsed]): Future[HelloReply] = {
    in.runWith(Sink.seq).map(elements => HelloReply(s"Hello, ${elements.map(_.name).mkString(", ")}"))
  }

  override def itKeepsReplying(in: HelloRequest): Source[HelloReply, NotUsed] = {
    Source(s"Hello, ${in.name}".toList).map(character => HelloReply(character.toString))
  }

  override def streamHellos(in: Source[HelloRequest, NotUsed]): Source[HelloReply, NotUsed] = {
    in.map(request => HelloReply(s"Hello, ${request.name}"))
  }
}
