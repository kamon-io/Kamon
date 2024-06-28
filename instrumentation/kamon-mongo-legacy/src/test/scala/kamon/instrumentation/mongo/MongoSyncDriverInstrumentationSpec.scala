package kamon.instrumentation.mongo

import com.mongodb.client.model.{Accumulators, Aggregates, Filters}
import kamon.tag.Lookups._
import kamon.testkit.{InitAndStopKamonAfterAll, TestSpanReporter}
import org.bson.Document
import org.scalatest.OptionValues
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers

import java.util
import scala.concurrent.duration._

class MongoSyncDriverInstrumentationSpec extends EmbeddedMongoTest(port = 4445) with Matchers with TestSpanReporter
    with Eventually with OptionValues with InitAndStopKamonAfterAll {

  val client = syncClient()
  val tools = client.getDatabase("test").getCollection("tools")

  "the MongoDB Driver Instrumentation" should {

    "create spans for aggregate" in {
      tools.insertOne(new Document("name", "kamon").append("aggregatable", true).append("license", "apache"))
      tools.insertOne(new Document("name", "zipkin").append("aggregatable", true).append("license", "apache"))
      tools.insertOne(new Document("name", "prometheus").append("aggregatable", true).append("license", "apache"))
      tools.insertOne(new Document("name", "linux").append("aggregatable", true).append("license", "gpl"))

      val aggregateResults = tools.aggregate(util.Arrays.asList(
        Aggregates.`match`(Filters.eq("aggregatable", true)),
        Aggregates.group("$license", Accumulators.sum("count", 1))
      )).batchSize(1).iterator()

      val mainSpan = eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "tools.aggregate"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
        span
      }

      while (aggregateResults.hasNext) {
        aggregateResults.next()
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
      tools.insertOne(new Document("name", "kamon").append("countable", true))
      tools.insertOne(new Document("name", "zipkin").append("countable", true))
      tools.insertOne(new Document("name", "prometheus").append("countable", true))
      tools.countDocuments(new Document("countable", true))

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "tools.countDocuments"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
      }
    }

    "create spans for deleteMany" in {
      tools.insertOne(new Document("name", "kamon").append("deletable", true))
      tools.insertOne(new Document("name", "zipkin").append("deletable", true))
      tools.insertOne(new Document("name", "prometheus").append("deletable", true))
      tools.deleteMany(new Document("deletable", true))

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
      tools.insertOne(new Document("name", "kamon").append("deleteOne", true))
      tools.insertOne(new Document("name", "zipkin"))
      tools.insertOne(new Document("name", "prometheus"))
      tools.deleteOne(new Document("deleteOne", true))

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
      // NOTES: for some reason the distinct operation does not respect the batch size, it seems to be always
      //        fetching all the results from Mongo. If that behavior changes we should make sure that any getMore
      //        operations are covered by this test.

      tools.insertOne(new Document("name", "kamon").append("distinct", "one"))
      tools.insertOne(new Document("name", "zipkin").append("distinct", "two"))
      tools.insertOne(new Document("name", "prometheus").append("distinct", "three"))
      tools.distinct("distinct", classOf[String])
        .batchSize(1)
        .iterator()

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "tools.distinct"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
      }
    }

    "create spans for find" in {
      tools.insertOne(new Document("name", "kamon").append("find", "yes"))
      tools.insertOne(new Document("name", "zipkin").append("find", "yes"))
      tools.insertOne(new Document("name", "prometheus").append("find", "yes"))
      val results = tools.find(new Document("find", "yes"))
        .batchSize(2)
        .iterator()

      val mainSpan = eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "tools.find"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
        span
      }

      // We are exhausting the iterator to for the getMore operation to happen
      while (results.hasNext) {
        results.next()
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
      tools.insertOne(new Document("name", "kamon").append("findAndDelete", "one"))
      tools.insertOne(new Document("name", "zipkin").append("findAndDelete", "two"))
      tools.insertOne(new Document("name", "prometheus").append("findAndDelete", "three"))
      tools.findOneAndDelete(new Document("findAndDelete", "one"))

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "tools.findOneAndDelete"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
      }
    }

    "create spans for findOneAndReplace" in {
      tools.insertOne(new Document("name", "kamon").append("findOneAndReplace", "one"))
      tools.insertOne(new Document("name", "zipkin").append("findOneAndReplace", "two"))
      tools.insertOne(new Document("name", "prometheus").append("findOneAndReplace", "three"))
      tools.findOneAndReplace(
        new Document("findOneAndReplace", "one"),
        new Document("name", "kamon").append("findOneAndReplace", "replaced")
      )

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "tools.findOneAndReplace"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
      }
    }

    "create spans for findOneAndUpdate" in {
      tools.insertOne(new Document("name", "kamon").append("findOneAndUpdate", "one"))
      tools.insertOne(new Document("name", "zipkin").append("findOneAndUpdate", "two"))
      tools.insertOne(new Document("name", "prometheus").append("findOneAndUpdate", "three"))
      tools.findOneAndUpdate(
        new Document("findOneAndUpdate", "one"),
        new Document("$set", new Document("name", "kamon").append("findOneAndUpdate", "updated"))
      )

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "tools.findOneAndUpdate"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
      }
    }

    "create spans for insertMany" in {
      tools.insertMany(java.util.Collections.singletonList(new Document("name", "zipkin")))

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "tools.insertMany"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
      }
    }

    "create spans for insertOne" in {
      tools.insertOne(new Document("name", "kamon"))

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "tools.insertOne"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
      }
    }

    "create spans for mapReduce" in {

      // NOTES: for some reason the mapReduce operation does not respect the batch size, it seems to be always
      //        fetching all the results from Mongo. If that behavior changes we should make sure that any getMore
      //        operations are covered by this test.

      tools.insertOne(new Document("name", "kamon").append("reduce", true).append("license", "apache").append(
        "value",
        100
      ))
      tools.insertOne(new Document("name", "zipkin").append("reduce", true).append("license", "apache").append(
        "value",
        100
      ))
      tools.insertOne(new Document("name", "prometheus").append("reduce", true).append("license", "apache").append(
        "value",
        100
      ))
      tools.insertOne(new Document("name", "linux").append("reduce", true).append("license", "gpl").append(
        "value",
        100
      ))

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
      ).batchSize(2).iterator()

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "tools.mapReduce"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
        span
      }
    }

    "create spans for replaceOne" in {
      tools.insertOne(new Document("name", "kamon").append("replaceOne", "one"))
      tools.insertOne(new Document("name", "zipkin").append("replaceOne", "two"))
      tools.insertOne(new Document("name", "prometheus").append("replaceOne", "three"))
      tools.replaceOne(
        new Document("replaceOne", "one"),
        new Document("name", "kamon").append("replaceOne", "replaced")
      )

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "tools.replaceOne"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
      }
    }

    "create spans for updateMany" in {
      tools.insertOne(new Document("name", "kamon").append("updateMany", "yes"))
      tools.insertOne(new Document("name", "zipkin").append("updateMany", "yes"))
      tools.insertOne(new Document("name", "prometheus").append("updateMany", "no"))
      tools.updateMany(
        new Document("updateMany", "yes"),
        new Document("$set", new Document("name", "kamon").append("updateMany", "updated"))
      )

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "tools.updateMany"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
        span.tags.get(plainBoolean("mongo.bulk.ack")) shouldBe true
        span.tags.get(plainLong("mongo.bulk.modified")) shouldBe 2L
      }
    }

    "create spans for updateOne" in {
      tools.insertOne(new Document("name", "kamon").append("updateOne", "yes"))
      tools.insertOne(new Document("name", "zipkin").append("updateOne", "no"))
      tools.insertOne(new Document("name", "prometheus").append("updateOne", "no"))
      tools.updateOne(
        new Document("updateOne", "yes"),
        new Document("$set", new Document("name", "kamon").append("updateOne", "updated"))
      )

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "tools.updateOne"
        span.metricTags.get(plain("db.instance")) shouldBe "test"
        span.metricTags.get(plain("mongo.collection")) shouldBe "tools"
        span.tags.get(plainBoolean("mongo.bulk.ack")) shouldBe true
        span.tags.get(plainLong("mongo.bulk.modified")) shouldBe 1L
      }
    }

  }
}
