package kamon.instrumentation.mongo

import com.mongodb.client.{MongoClient => SyncMongoClient, MongoClients => SyncMongoClients}
import com.mongodb.reactivestreams.client.{MongoClient => ReactiveMongoClient}
import de.flapdoodle.embed.mongo.config.{MongodConfigBuilder, Net}
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.{MongodExecutable, MongodProcess, MongodStarter}
import org.mongodb.scala.{MongoClient => ScalaMongoClient}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

abstract class EmbeddedMongoTest(port: Int) extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private val starter = MongodStarter.getDefaultInstance()
  private var mongodExecutable: MongodExecutable = _
  private var mongodProcess: MongodProcess = _

  protected def reactiveClient(): ReactiveMongoClient = {
    ???
  }

  protected def syncClient(): SyncMongoClient = {
    SyncMongoClients.create(s"mongodb://localhost:${port}")
  }

  protected def scalaClient(): ScalaMongoClient = {
    ScalaMongoClient(s"mongodb://localhost:${port}")
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    mongodExecutable = starter.prepare(new MongodConfigBuilder()
      .version(Version.Main.V4_0)
      .net(new Net(port, false))
      .build())

    mongodProcess = mongodExecutable.start()
  }

  override protected def afterAll(): Unit = {
    mongodProcess.stop()
    mongodExecutable.stop()

    super.afterAll()
  }

}
