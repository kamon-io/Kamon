package kamon.instrumentation.mongo

import kamon.tag.Lookups._
import kamon.testkit.{InitAndStopKamonAfterAll, TestSpanReporter}
import org.mongodb.scala._
import org.mongodb.scala.model.{Accumulators, Aggregates, Filters, Updates}
import org.scalatest.OptionValues
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.Await
import scala.concurrent.duration._

class MongoScalaDriverInstrumentationSpec extends EmbeddedMongoTest(port = 4445) with Matchers with TestSpanReporter
    with Eventually with OptionValues with InitAndStopKamonAfterAll {

  val client = scalaClient()
  val tools = client.getDatabase("test").getCollection("tools")

  "the MongoDB Driver Instrumentation" should {

    "create spans for aggregate" in {
      tools.insertOne(Document("name" -> "kamon", "aggregatable" -> true, "license" -> "apache")).consume()
      tools.insertOne(Document("name" -> "zipkin", "aggregatable" -> true, "license" -> "apache")).consume()
      tools.insertOne(Document("name" -> "prometheus", "aggregatable" -> true, "license" -> "apache")).consume()
      tools.insertOne(Document("name" -> "linux", "aggregatable" -> true, "license" -> "gpl")).consume()

      tools.aggregate(Seq(
        Aggregates.`match`(Filters.eq("aggregatable", true)),
        Aggregates.group("$license", Accumulators.sum("count", 1))
      )).batchSize(1).consume()

      val mainSpan = eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "tools.aggregate"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
        span
      }

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.parentId shouldBe mainSpan.id
        span.trace shouldBe mainSpan.trace
        span.operationName shouldBe "tools.aggregate.getMore"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
      }
    }

    "create spans for countDocuments" in {
      tools.insertOne(Document("name" -> "kamon", "countable" -> true)).consume()
      tools.insertOne(Document("name" -> "zipkin", "countable" -> true)).consume()
      tools.insertOne(Document("name" -> "prometheus", "countable" -> true)).consume()
      tools.countDocuments(Filters.equal("countable", true)).consume()

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "tools.countDocuments"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
      }
    }

    "create spans for deleteMany" in {
      tools.insertOne(Document("name" -> "kamon", "deletable" -> true)).consume()
      tools.insertOne(Document("name" -> "zipkin", "deletable" -> true)).consume()
      tools.insertOne(Document("name" -> "prometheus", "deletable" -> true)).consume()
      tools.deleteMany(Filters.equal("deletable", true)).consume()

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "tools.deleteMany"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
        span.tags.get(plainBoolean("mongo.bulk.ack")) shouldBe true
        span.tags.get(plainLong("mongo.bulk.deleted")) shouldBe 3L
      }
    }

    "create spans for deleteOne" in {
      tools.insertOne(Document("name" -> "kamon", "deleteOne" -> true)).consume()
      tools.insertOne(Document("name" -> "zipkin", "deleteOne" -> true)).consume()
      tools.insertOne(Document("name" -> "prometheus", "deleteOne" -> true)).consume()
      tools.deleteOne(Filters.equal("deleteOne", true)).consume()

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "tools.deleteOne"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
        span.tags.get(plainBoolean("mongo.bulk.ack")) shouldBe true
        span.tags.get(plainLong("mongo.bulk.deleted")) shouldBe 1L
      }
    }

    "create spans for distinct" in {
      tools.insertOne(Document("name" -> "kamon", "distinct" -> "one")).consume()
      tools.insertOne(Document("name" -> "zipkin", "distinct" -> "two")).consume()
      tools.insertOne(Document("name" -> "prometheus", "distinct" -> "three")).consume()
      tools.distinct[String]("distinct").consume()

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "tools.distinct"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
      }
    }

    "create spans for find" in {
      tools.insertOne(Document("name" -> "kamon", "findable" -> true)).consume()
      tools.insertOne(Document("name" -> "zipkin", "findable" -> true)).consume()
      tools.insertOne(Document("name" -> "prometheus", "findable" -> true)).consume()
      tools.insertOne(Document("name" -> "linux", "findable" -> true)).consume()
      tools.find(Document("findable" -> true)).batchSize(1).consume()

      val mainSpan = eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "tools.find"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
        span
      }

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.parentId shouldBe mainSpan.id
        span.trace shouldBe mainSpan.trace
        span.operationName shouldBe "tools.find.getMore"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
      }
    }

    "create spans for findOneAndDelete" in {
      tools.insertOne(Document("name" -> "kamon", "findAndDelete" -> "one")).consume()
      tools.insertOne(Document("name" -> "zipkin", "findAndDelete" -> "two")).consume()
      tools.insertOne(Document("name" -> "prometheus", "findAndDelete" -> "three")).consume()
      tools.findOneAndDelete(Filters.equal("findAndDelete", "one")).consume()

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "tools.findOneAndDelete"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
      }
    }

    "create spans for findOneAndReplace" in {
      tools.insertOne(Document("name" -> "kamon", "findOneAndReplace" -> "one")).consume()
      tools.insertOne(Document("name" -> "zipkin", "findOneAndReplace" -> "two")).consume()
      tools.insertOne(Document("name" -> "prometheus", "findOneAndReplace" -> "three")).consume()
      tools.findOneAndReplace(
        Filters.equal("findOneAndReplace", "one"),
        Document("name" -> "kamon", "findOneAndReplace" -> "four")
      ).consume()

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "tools.findOneAndReplace"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
      }
    }

    "create spans for findOneAndUpdate" in {
      tools.insertOne(Document("name" -> "kamon", "findOneAndUpdate" -> "one")).consume()
      tools.insertOne(Document("name" -> "zipkin", "findOneAndUpdate" -> "two")).consume()
      tools.insertOne(Document("name" -> "prometheus", "findOneAndUpdate" -> "three")).consume()
      tools.findOneAndUpdate(
        Filters.equal("findOneAndUpdate", "one"),
        Updates.set("findOneAndUpdate", "four")
      ).consume()

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "tools.findOneAndUpdate"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
      }
    }

    "create spans for insertMany" in {
      tools.insertMany(Seq(Document("name" -> "kamon"), Document("name" -> "zipkin"))).consume()

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "tools.insertMany"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
        span.tags.get(plainBoolean("mongo.bulk.ack")) shouldBe true
        span.tags.get(plainLong("mongo.bulk.inserted")) shouldBe 2L
      }
    }

    "create spans for insertOne" in {
      tools.insertOne(Document("name" -> "kamon")).consume()

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "tools.insertOne"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
        span.tags.get(plainBoolean("mongo.bulk.ack")) shouldBe true
        span.tags.get(plainLong("mongo.bulk.inserted")) shouldBe 1L
      }
    }

    "create spans for mapReduce" in {

      // NOTES: for some reason the mapReduce operation does not respect the batch size, it seems to be always
      //        fetching all the results from Mongo. If that behavior changes we should make sure that any getMore
      //        operations are covered by this test.

      tools.insertOne(Document("name" -> "kamon", "reduce" -> true, "license" -> "apache", "value" -> 100)).consume()
      tools.insertOne(Document("name" -> "zipkin", "reduce" -> true, "license" -> "apache", "value" -> 100)).consume()
      tools.insertOne(
        Document("name" -> "prometheus", "reduce" -> true, "license" -> "apache", "value" -> 100)
      ).consume()
      tools.insertOne(Document("name" -> "linux", "reduce" -> true, "license" -> "gpl", "value" -> 100)).consume()

      tools.mapReduce(
        """
          |function() {
          |  if(this.license !== undefined) {
          |    emit(this.license + "_0", this.value)
          |    emit(this.license + "_1", this.value)
          |    emit(this.license + "_2", this.value)
          |  }
          |}
        """.stripMargin,
        """
          |function(key, values) {
          |  return Array.sum(values)
          |}
        """.stripMargin
      ).batchSize(2).consume()

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "tools.mapReduce"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
      }
    }

    "create spans for mapReduceToCollection" in {

      // NOTES: for some reason the mapReduce operation does not respect the batch size, it seems to be always
      //        fetching all the results from Mongo. If that behavior changes we should make sure that any getMore
      //        operations are covered by this test.

      tools.insertOne(Document("name" -> "kamon", "reduce" -> true, "license" -> "apache", "value" -> 100)).consume()
      tools.insertOne(Document("name" -> "zipkin", "reduce" -> true, "license" -> "apache", "value" -> 100)).consume()
      tools.insertOne(
        Document("name" -> "prometheus", "reduce" -> true, "license" -> "apache", "value" -> 100)
      ).consume()
      tools.insertOne(Document("name" -> "linux", "reduce" -> true, "license" -> "gpl", "value" -> 100)).consume()

      tools.mapReduce(
        """
          |function() {
          |  if(this.license !== undefined) {
          |    emit(this.license + "_0", this.value)
          |    emit(this.license + "_1", this.value)
          |    emit(this.license + "_2", this.value)
          |  }
          |}
        """.stripMargin,
        """
          |function(key, values) {
          |  return Array.sum(values)
          |}
        """.stripMargin
      ).collectionName("mapReducedCollection").toCollection().consume()

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "tools.mapReduceToCollection"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
      }
    }

    "create spans for replaceOne" in {
      tools.insertOne(Document("name" -> "kamon", "replaceOne" -> "one")).consume()
      tools.insertOne(Document("name" -> "zipkin", "replaceOne" -> "two")).consume()
      tools.insertOne(Document("name" -> "prometheus", "replaceOne" -> "one")).consume()
      tools.replaceOne(
        Filters.equal("replaceOne", "one"),
        Document("name" -> "kamon", "replaceOne" -> "four")
      ).consume()

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "tools.replaceOne"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
        span.tags.get(plainLong("mongo.bulk.modified")) shouldBe 1L
      }
    }

    "create spans for updateMany" in {
      tools.insertOne(Document("name" -> "kamon", "updateMany" -> "one")).consume()
      tools.insertOne(Document("name" -> "zipkin", "updateMany" -> "one")).consume()
      tools.insertOne(Document("name" -> "prometheus", "updateMany" -> "one")).consume()
      tools.updateMany(
        Filters.equal("updateMany", "one"),
        Updates.set("findOneAndUpdate", "four")
      ).consume()

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "tools.updateMany"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
        span.tags.get(plainLong("mongo.bulk.modified")) shouldBe 3L
      }
    }

    "create spans for updateOne" in {
      tools.insertOne(Document("name" -> "kamon", "updateOne" -> "one")).consume()
      tools.insertOne(Document("name" -> "zipkin", "updateOne" -> "one")).consume()
      tools.insertOne(Document("name" -> "prometheus", "updateOne" -> "one")).consume()
      tools.updateOne(
        Filters.equal("updateOne", "one"),
        Updates.set("findOneAndUpdate", "four")
      ).consume()

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "tools.updateOne"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
        span.tags.get(plainLong("mongo.bulk.modified")) shouldBe 1L
      }
    }

  }

  implicit class RichSingleObservable[T](observable: Observable[T]) {

    // Shorthand for just consuming results from an observable.
    def consume(): Unit = {
      Await.result(observable.toFuture(), 10 seconds)
    }
  }
}
