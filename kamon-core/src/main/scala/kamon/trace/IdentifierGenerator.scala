package kamon.trace

import java.nio.ByteBuffer
import java.util.concurrent.ThreadLocalRandom

import kamon.util.HexCodec

import scala.util.Try

trait IdentityProvider {
  def traceIdentifierGenerator(): IdentityProvider.Generator
  def spanIdentifierGenerator(): IdentityProvider.Generator
}

object IdentityProvider {
  case class Identifier(string: String, bytes: Array[Byte])

  val NoIdentifier = Identifier("", new Array[Byte](0))


  trait Generator {
    def generate(): Identifier
    def from(string: String): Identifier
    def from(bytes: Array[Byte]): Identifier
  }


  class Default extends IdentityProvider {
    private val generator = new Generator {
      override def generate(): Identifier = {
        val data = ByteBuffer.wrap(new Array[Byte](8))
        val random = ThreadLocalRandom.current().nextLong()
        data.putLong(random)

        Identifier(HexCodec.toLowerHex(random), data.array())
      }

      override def from(string: String): Identifier =  Try {
        val identifierLong = HexCodec.lowerHexToUnsignedLong(string)
        val data = ByteBuffer.allocate(8)
        data.putLong(identifierLong)

        Identifier(string, data.array())
      } getOrElse(IdentityProvider.NoIdentifier)

      override def from(bytes: Array[Byte]): Identifier = Try {
        val buffer = ByteBuffer.wrap(bytes)
        val identifierLong = buffer.getLong

        Identifier(HexCodec.toLowerHex(identifierLong), bytes)
      } getOrElse(IdentityProvider.NoIdentifier)
    }

    override def traceIdentifierGenerator(): Generator = generator
    override def spanIdentifierGenerator(): Generator = generator
  }
}