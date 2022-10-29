package kamon.instrumentation.redisson

import io.netty.buffer.ByteBuf
import io.netty.channel.{ChannelFuture, ChannelFutureListener}
import kamon.Kamon
import kamon.trace.Span
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice
import org.redisson.client.protocol.{CommandData, CommandsData}

import scala.collection.JavaConverters._

class RedissonInstrumentation extends InstrumentationBuilder {

  onType("org.redisson.client.RedisConnection")
    .advise(method("send").and(takesArguments(1)), classOf[RedisConnectionInstrumentation])

}

class RedisConnectionInstrumentation

object RedisConnectionInstrumentation {
  @Advice.OnMethodEnter()
  def enter(@Advice.Argument(0) command: Any): Span = {

    def parseCommand(command: CommandData[_, _]): String = {
      command.getParams.map {
        case _: ByteBuf => ""
        case bytes => String.valueOf(bytes)
      }.filter(_.nonEmpty).mkString(" ")
    }

    val (commandName, statements) = command match {
      case commands: CommandsData =>
        val spanName = "redis.command.batch_execute"
        val statements = commands.getCommands.asScala.map(parseCommand).mkString(",")
        (spanName, statements)
      case command: CommandData[_, _] =>
        val spanName = s"redis.command.${command.getCommand.getName}"
        val statement = parseCommand(command)
        (spanName, statement)
    }

    Kamon.clientSpanBuilder(commandName, "redisson")
      .tag("db.statement", statements)
      .tagMetrics("db.system", "redis")
      .start()
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  def exit(@Advice.Enter span: Span,
           @Advice.Thrown t: Throwable,
           @Advice.Return future: ChannelFuture) = {
    if (t != null) {
      span.fail(t)
    }
    future.addListener(new ChannelFutureListener() {
      override def operationComplete(future: ChannelFuture): Unit = {
        if (future.isSuccess) {
          span.finish()
        } else {
          span.fail(future.cause())
          span.finish()
        }
      }
    })
  }
}