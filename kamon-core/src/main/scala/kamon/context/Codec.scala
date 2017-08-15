package kamon
package context

import com.typesafe.config.Config
import kamon.trace.IdentityProvider
import kamon.util.DynamicAccess
import org.slf4j.LoggerFactory

import scala.collection.mutable

class Codec(initialConfig: Config) {
  private val log = LoggerFactory.getLogger(classOf[Codec])

  @volatile private var httpHeaders: Codec.ForContext[TextMap] = new Codec.HttpHeaders(Map.empty)
  //val Binary: Codec.ForContext[ByteBuffer] = _
  reconfigure(initialConfig)


  def HttpHeaders: Codec.ForContext[TextMap] =
    httpHeaders

  def reconfigure(config: Config): Unit = {
    httpHeaders = new Codec.HttpHeaders(readEntryCodecs("kamon.context.encoding.http-headers", config))
  }

  private def readEntryCodecs[T](rootKey: String, config: Config): Map[String, Codec.ForEntry[T]] = {
    val rootConfig = config.getConfig(rootKey)
    val dynamic = new DynamicAccess(getClass.getClassLoader)
    val entries = Map.newBuilder[String, Codec.ForEntry[T]]

    rootConfig.topLevelKeys.foreach(key => {
      try {
        val fqcn = rootConfig.getString(key)
        entries += ((key, dynamic.createInstanceFor[Codec.ForEntry[T]](fqcn, Nil).get))
      } catch {
        case e: Throwable =>
          log.error(s"Failed to initialize codec for key [$key]", e)
      }
    })

    entries.result()
  }
}

object Codec {

  trait ForContext[T] {
    def encode(context: Context): T
    def decode(carrier: T): Context
  }

  trait ForEntry[T] {
    def encode(context: Context): T
    def decode(carrier: T, context: Context): Context
  }

  final class HttpHeaders(entryCodecs: Map[String, Codec.ForEntry[TextMap]]) extends Codec.ForContext[TextMap] {
    private val log = LoggerFactory.getLogger(classOf[HttpHeaders])

    override def encode(context: Context): TextMap = {
      val encoded = TextMap.Default()

      context.entries.foreach {
        case (key, _) if key.broadcast =>
          entryCodecs.get(key.name) match {
            case Some(codec) =>
              try {
                codec.encode(context).values.foreach(pair => encoded.put(pair._1, pair._2))
              } catch {
                case e: Throwable => log.error(s"Failed to encode key [${key.name}]", e)
              }

            case None =>
              log.error("Context key [{}] should be encoded in HttpHeaders but no codec was found for it.", key.name)
          }
      }

      encoded
    }

    override def decode(carrier: TextMap): Context = {
      var context: Context = Context.Empty

      try {
        context = entryCodecs.foldLeft(context)((ctx, codecEntry) => {
          val (_, codec) = codecEntry
          codec.decode(carrier, ctx)
        })

      } catch {
        case e: Throwable =>
          log.error("Failed to decode context from HttpHeaders", e)
      }

      context
    }
  }
}


trait TextMap {

  def get(key: String): Option[String]

  def put(key: String, value: String): Unit

  def values: Iterator[(String, String)]
}

object TextMap {

  class Default extends TextMap {
    private val storage =
      mutable.Map.empty[String, String]

    override def get(key: String): Option[String] =
      storage.get(key)

    override def put(key: String, value: String): Unit =
      storage.put(key, value)

    override def values: Iterator[(String, String)] =
      storage.toIterator
  }

  object Default {
    def apply(): Default =
      new Default()
  }
}