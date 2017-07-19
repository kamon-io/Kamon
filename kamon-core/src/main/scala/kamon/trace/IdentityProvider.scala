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
  case class Identifier(string: String, bytes: Array[Byte]) {

    override def equals(obj: Any): Boolean = {
      if(obj != null && obj.isInstanceOf[Identifier])
        obj.asInstanceOf[Identifier].string == string
      else false
    }
  }

  val NoIdentifier = Identifier("", new Array[Byte](0))

  trait Generator {
    def generate(): Identifier
    def from(string: String): Identifier
    def from(bytes: Array[Byte]): Identifier
  }


  class Default extends IdentityProvider {
    protected val longGenerator = new Generator {
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

    override def traceIdentifierGenerator(): Generator = longGenerator
    override def spanIdentifierGenerator(): Generator = longGenerator
  }

  object Default {
    def apply(): Default = new Default()
  }


  class DoubleSizeTraceID extends Default {
    private val doubleLongGenerator = new Generator {
      override def generate(): Identifier = {
        val data = ByteBuffer.wrap(new Array[Byte](16))
        val highLong = ThreadLocalRandom.current().nextLong()
        val lowLong = ThreadLocalRandom.current().nextLong()
        data.putLong(highLong)
        data.putLong(lowLong)

        Identifier(HexCodec.toLowerHex(highLong) + HexCodec.toLowerHex(lowLong), data.array())
      }

      override def from(string: String): Identifier =  Try {
        val highPart = HexCodec.lowerHexToUnsignedLong(string.substring(0, 16))
        val lowPart = HexCodec.lowerHexToUnsignedLong(string.substring(16, 32))
        val data = ByteBuffer.allocate(16)
        data.putLong(highPart)
        data.putLong(lowPart)

        Identifier(string, data.array())
      } getOrElse(IdentityProvider.NoIdentifier)

      override def from(bytes: Array[Byte]): Identifier = Try {
        val buffer = ByteBuffer.wrap(bytes)
        val highLong = buffer.getLong
        val lowLong = buffer.getLong

        Identifier(HexCodec.toLowerHex(highLong) + HexCodec.toLowerHex(lowLong), bytes)
      } getOrElse(IdentityProvider.NoIdentifier)
    }

    override def traceIdentifierGenerator(): Generator = doubleLongGenerator
  }

  object DoubleSizeTraceID {
    def apply(): DoubleSizeTraceID = new DoubleSizeTraceID()
  }
}