/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
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

package kamon.instrumentation
package mongo

import com.mongodb.MongoNamespace
import kamon.Kamon
import kamon.instrumentation.context.HasContext
import kamon.instrumentation.mongo.MongoClientInstrumentation.HasOperationName
import kamon.trace.{Span, SpanBuilder}
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice

class MongoClientInstrumentation extends InstrumentationBuilder {

  /**
    * This first section just ensures that operations will get the right name, specially the ones that are
    * implemented as bulk writes to Mongo. In the cases where we know that the name will not be captured properly
    * we are explicitly adding the operation name to the operation instance so that it can be used when naming
    * the Spans.
    */
  val OperationsAdviceFQCN = "kamon.instrumentation.mongo.CopyOperationNameIntoMixedBulkWriteOperation"

  onType("com.mongodb.internal.operation.Operations")
    .when(classIsPresent("com.mongodb.internal.operation.Operations"))
    .advise(method("deleteOne"), OperationsAdviceFQCN)
    .advise(method("deleteMany"), OperationsAdviceFQCN)
    .advise(method("findOneAndDelete"), OperationsAdviceFQCN)
    .advise(method("findOneAndReplace"), OperationsAdviceFQCN)
    .advise(method("findOneAndUpdate"), OperationsAdviceFQCN)
    .advise(method("insertOne"), OperationsAdviceFQCN)
    .advise(method("insertMany"), OperationsAdviceFQCN)
    .advise(method("replaceOne"), OperationsAdviceFQCN)
    .advise(method("updateMany"), OperationsAdviceFQCN)
    .advise(method("updateOne"), OperationsAdviceFQCN)

  onType("com.mongodb.internal.operation.MixedBulkWriteOperation")
    .when(classIsPresent("com.mongodb.internal.operation.Operations"))
    .mixin(classOf[MongoClientInstrumentation.HasOperationName.Mixin])

  onSubTypesOf("com.mongodb.internal.operation.BaseFindAndModifyOperation")
    .when(classIsPresent("com.mongodb.internal.operation.Operations"))
    .mixin(classOf[MongoClientInstrumentation.HasOperationName.Mixin])

  /**
    * These are the actual classes we are instrumenting to track operations. Since the same classes are used for
    * both Sync and Async variants (and the Async driver is used for the Reactive Streams and Scala Drivers) we
    * are going for a unified instrumentation that only needs special treatment around the execute/executeAsync
    * methods.
    */
  val instrumentedOperations = Seq(
    "com.mongodb.internal.operation.AggregateOperationImpl",
    "com.mongodb.internal.operation.CountOperation",
    "com.mongodb.internal.operation.DistinctOperation",
    "com.mongodb.internal.operation.FindOperation",
    "com.mongodb.internal.operation.BaseFindAndModifyOperation",
    "com.mongodb.internal.operation.MapReduceWithInlineResultsOperation",
    "com.mongodb.internal.operation.MapReduceToCollectionOperation",
    "com.mongodb.internal.operation.MixedBulkWriteOperation"
  )

  onTypes(instrumentedOperations: _*)
    .when(classIsPresent("com.mongodb.internal.operation.Operations"))
    .advise(method("execute"), classOf[ExecuteOperationAdvice])
    .advise(method("executeAsync"), classOf[ExecuteAsyncOperationAdvice])

  /**
    * Ensures that calls to .getMore() inside a BatchCursor/AsyncBatchCursor will generate Spans for the round trip
    * to Mongo and that those Spans will be children of the first operation that created BatchCursor/AsyncBatchCursor.
    */
  onType("com.mongodb.internal.operation.AsyncQueryBatchCursor")
    .when(classIsPresent("com.mongodb.internal.operation.Operations"))
    .mixin(classOf[HasContext.Mixin])
    .advise(method("getMore").and(takesArguments(4)), classOf[AsyncBatchCursorGetMoreAdvice])

  onType("com.mongodb.internal.operation.QueryBatchCursor")
    .when(classIsPresent("com.mongodb.internal.operation.Operations"))
    .mixin(classOf[HasContext.VolatileMixin])
    .advise(method("getMore"), classOf[BatchCursorGetMoreAdvice])

}

class CopyOperationNameIntoMixedBulkWriteOperation

object CopyOperationNameIntoMixedBulkWriteOperation {

  @Advice.OnMethodExit
  def exit(@Advice.Return writeOperation: Any, @Advice.Origin("#m") methodName: String): Unit = {
    writeOperation match {
      case hon: HasOperationName => hon.setName(methodName)
      case _                     =>
    }
  }
}

object MongoClientInstrumentation {

  /**
    * Assists with keeping the actual operation name on all write operations since they are all backed by the
    * MixedBulkWriteOperation class.
    */
  trait HasOperationName {
    def name: String

    def setName(name: String): Unit
  }

  object HasOperationName {

    class Mixin(@volatile var name: String) extends HasOperationName {
      override def setName(name: String): Unit = this.name = name
    }

  }

  val OperationClassToOperation = Map(
    "com.mongodb.internal.operation.AggregateOperationImpl" -> "aggregate",
    "com.mongodb.internal.operation.CountOperation" -> "countDocuments",
    "com.mongodb.internal.operation.DistinctOperation" -> "distinct",
    "com.mongodb.internal.operation.FindOperation" -> "find",
    "com.mongodb.internal.operation.FindAndDeleteOperation" -> "findAndDelete",
    "com.mongodb.internal.operation.MapReduceWithInlineResultsOperation" -> "mapReduce",
    "com.mongodb.internal.operation.MapReduceToCollectionOperation" -> "mapReduceToCollection"
  )

  def clientSpanBuilder(namespace: MongoNamespace, operationClassName: String): SpanBuilder = {
    val mongoOperationName = OperationClassToOperation.getOrElse(operationClassName, operationClassName)
    val spanOperationName = namespace.getCollectionName + "." + mongoOperationName

    Kamon.clientSpanBuilder(spanOperationName, "mongo.driver.sync")
      .tagMetrics("db.instance", namespace.getDatabaseName)
      .tagMetrics("mongo.collection", namespace.getCollectionName)
  }

  def getMoreSpanBuilder(parentSpan: Span, namespace: MongoNamespace): SpanBuilder = {
    val spanOperationName = parentSpan.operationName() + ".getMore"

    Kamon.clientSpanBuilder(spanOperationName, "mongo.driver.sync")
      .asChildOf(parentSpan)
      .tagMetrics("db.instance", namespace.getDatabaseName)
      .tagMetrics("mongo.collection", namespace.getCollectionName)
  }
}
