package kamon.instrumentation.mongo

import com.mongodb.{ConnectionString, MongoClientSettings}
import com.mongodb.client.MongoClients
import com.mongodb.event.{CommandFailedEvent, CommandListener, CommandStartedEvent, CommandSucceededEvent}
import kamon.Kamon
import org.bson.Document

object QuickTest extends App {
  Kamon.init()
  Kamon.registerModule("printlnr", new PrintlnReporter())
  println("Sampler is: " + Kamon.config().getString("kamon.trace.sampler"))

  val settings = MongoClientSettings.builder()
    .addCommandListener(new MyListener())
    .applyConnectionString(new ConnectionString("mongodb://localhost:32768"))
    .build()

  val client = MongoClients.create(settings)
  val tools = client.getDatabase("test").getCollection("tools")


//  for(i <- 1 to 100) {
//    tools.insertOne(new Document("name", s"person_${i}"))
//  }

  println("START")
  val iterator = tools.find().iterator()
  tools.find().iterator()
  tools.find().iterator()
  while(iterator.hasNext) {
    println("next " + iterator.next())
  }





  println("finished")


  Thread.sleep(500000)
}

class MyListener extends CommandListener {

  override def commandStarted(event: CommandStartedEvent): Unit = {
    println(s"[${Thread.currentThread().getName}] Starting command: " + event.getCommandName + ", " + event.getCommand.toJson)
  }

  override def commandSucceeded(event: CommandSucceededEvent): Unit = {
    println(s"[${Thread.currentThread().getName}] Succeeded command: " + event)
  }

  override def commandFailed(event: CommandFailedEvent): Unit = {
    println(s"[${Thread.currentThread().getName}] Failed command: " + event)
  }
}
