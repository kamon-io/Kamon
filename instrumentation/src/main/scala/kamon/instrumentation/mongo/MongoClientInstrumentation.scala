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

  when(classIsPresent("com.mongodb.internal.operation.Operations"), new Runnable {
    override def run(): Unit = {

      /**
        * This first section just ensures that operations will get the right name, specially the ones that are
        * implemented as bulk writes to Mongo. In the cases where we know that the name will not be captured properly
        * we are explicitly adding the operation name to the operation instance so that it can be used when naming
        * the Spans.
        */
      onType("com.mongodb.operation.MixedBulkWriteOperation")
        .mixin(classOf[MongoClientInstrumentation.HasOperationName.Mixin])

      onSubTypesOf("com.mongodb.operation.BaseFindAndModifyOperation")
        .mixin(classOf[MongoClientInstrumentation.HasOperationName.Mixin])

      onType("com.mongodb.internal.operation.Operations")
        .advise(method("deleteOne"), CopyOperationNameIntoMixedBulkWriteOperation)
        .advise(method("deleteMany"), CopyOperationNameIntoMixedBulkWriteOperation)
        .advise(method("findOneAndDelete"), CopyOperationNameIntoMixedBulkWriteOperation)
        .advise(method("findOneAndReplace"), CopyOperationNameIntoMixedBulkWriteOperation)
        .advise(method("findOneAndUpdate"), CopyOperationNameIntoMixedBulkWriteOperation)
        .advise(method("insertOne"), CopyOperationNameIntoMixedBulkWriteOperation)
        .advise(method("insertMany"), CopyOperationNameIntoMixedBulkWriteOperation)
        .advise(method("replaceOne"), CopyOperationNameIntoMixedBulkWriteOperation)
        .advise(method("updateMany"), CopyOperationNameIntoMixedBulkWriteOperation)
        .advise(method("updateOne"), CopyOperationNameIntoMixedBulkWriteOperation)


      /**
        * These are the actual classes we are instrumenting to track operations. Since the same classes are used for
        * both Sync and Async variants (and the Asyn driver is used for the Reactive Streams and Scala Drivers) we
        * are going for a unified instrumentation that only needs special treatment around the execute/executeAsync
        * methods.
        */
      val instrumentedOperations = Seq(
        "com.mongodb.operation.AggregateOperationImpl",
        "com.mongodb.operation.CountOperation",
        "com.mongodb.operation.DistinctOperation",
        "com.mongodb.operation.FindOperation",
        "com.mongodb.operation.BaseFindAndModifyOperation",
        "com.mongodb.operation.MapReduceWithInlineResultsOperation",
        "com.mongodb.operation.MapReduceToCollectionOperation",
        "com.mongodb.operation.MixedBulkWriteOperation"
      )

      onTypes(instrumentedOperations: _*)
        .advise(method("execute"), classOf[ExecuteOperationAdvice])
        .advise(method("executeAsync"), classOf[ExecuteAsyncOperationAdvice])


      /**
        * Ensures that calls to .getMore() inside a BatchCursor/AsyncBatchCursor will generate Spans for the round trip
        * to Mongo and that those Spans will be children of the first operation that created BatchCursor/AsyncBatchCursor.
        */
      onType("com.mongodb.operation.AsyncQueryBatchCursor")
        .mixin(classOf[HasContext.VolatileMixin])
        .advise(method("getMore").and(takesArguments(4)), classOf[AsyncBatchCursorGetMoreAdvice])

      onType("com.mongodb.operation.QueryBatchCursor")
        .mixin(classOf[HasContext.VolatileMixin])
        .advise(method("getMore"), classOf[BatchCursorGetMoreAdvice])
    }
  })
}


object CopyOperationNameIntoMixedBulkWriteOperation {

  private val x = new MongoNamespace("FullName.Something")

  @Advice.OnMethodExit
  def exit(@Advice.Return writeOperation: Any, @Advice.Origin("#m") methodName: String): Unit = {
    writeOperation match {
      case hon: HasOperationName => hon.setName(methodName)
      case _ =>
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
    "com.mongodb.operation.AggregateOperationImpl" -> "aggregate",
    "com.mongodb.operation.CountOperation" -> "countDocuments",
    "com.mongodb.operation.DistinctOperation" -> "distinct",
    "com.mongodb.operation.FindOperation" -> "find",
    "com.mongodb.operation.FindAndDeleteOperation" -> "findAndDelete",
    "com.mongodb.operation.MapReduceWithInlineResultsOperation" -> "mapReduce",
    "com.mongodb.operation.MapReduceToCollectionOperation" -> "mapReduceToCollection"
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