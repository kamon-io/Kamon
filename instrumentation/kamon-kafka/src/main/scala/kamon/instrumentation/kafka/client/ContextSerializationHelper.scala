package kamon.instrumentation.kafka.client

import java.io.ByteArrayOutputStream

import kamon.Kamon
import kamon.context.{BinaryPropagation, Context}

/**
  * This helper function encapsulates the access to the nested Scala objects for usage in Java.
  *
  * Avoid ugly Java statements like this:
  *  Kamon.defaultBinaryPropagation().write(ctx, kamon.context.BinaryPropagation$ByteStreamWriter$.MODULE$.of(out));
  */
object ContextSerializationHelper {

 def toByteArray(ctx: Context): Array[Byte] = {
    val out = new ByteArrayOutputStream();
    Kamon.defaultBinaryPropagation().write(ctx, BinaryPropagation.ByteStreamWriter.of(out))
    out.toByteArray
  }

  def fromByteArray(input: Array[Byte]): Context = {
    Kamon.defaultBinaryPropagation().read(BinaryPropagation.ByteStreamReader.of(input))
  }
}
