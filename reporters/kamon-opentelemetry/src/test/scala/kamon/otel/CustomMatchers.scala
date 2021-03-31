/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kamon.otel

import com.google.protobuf.ByteString
import io.opentelemetry.proto.common.v1.KeyValue
import kamon.tag.TagSet
import kamon.trace.Identifier.Factory
import kamon.trace.Span.Kind
import kamon.trace.{Identifier, Span, Trace}
import kamon.trace.Trace.SamplingDecision
import org.scalatest.matchers.{MatchResult, Matcher}

import java.time.Instant

object CustomMatchers {
  val spanIDFactory = Factory.EightBytesIdentifier
  val traceIDFactory = Factory.SixteenBytesIdentifier

  /**
   * Converts the provided byte array to a hex string
   * @param buf
   * @return
   */
  def asHex(buf: Array[Byte]): String = buf.map("%02X" format _).mkString
  def now():Long = System.currentTimeMillis()
  def finishedSpan():Span.Finished = {
    val tagSet = TagSet.builder()
      .add("string.tag", "xyz")
      .add("boolean.tag", true)
      .add("long.tag", 69)
      .build()
    Span.Finished(
      spanIDFactory.generate(),
      Trace(traceIDFactory.generate(), SamplingDecision.Sample),
      Identifier.Empty,
      "TestOperation",
      false,
      false,
      Instant.ofEpochMilli(now()-500),
      Instant.now(),
      Kind.Server,
      Span.Position.Unknown,
      tagSet,
      TagSet.Empty,
      Nil,
      Nil
    )
  }

  trait ByteStringMatchers {
    def equal(identifier: Identifier):Matcher[ByteString] = new Matcher[ByteString] {
      def apply(left: ByteString) = {
        MatchResult(
          left.toByteArray.sameElements(identifier.bytes),
          s"The identifiers don't match [${asHex(left.toByteArray)}] != [${identifier.string}]",
          "The identifiers match"
        )
      }
    }
    def be8Bytes:Matcher[ByteString] = beOfLength(8)
    def be16Bytes:Matcher[ByteString] = beOfLength(16)
    private def beOfLength(expectedLength:Int):Matcher[ByteString] = new Matcher[ByteString] {
      def apply(left: ByteString) = {
        MatchResult(
          left.toByteArray.size == expectedLength,
          s"The identifiers did not have expected length [${left.toByteArray.length}] != [$expectedLength]",
          "The identifier is of correct length"
        )
      }
    }
  }

  trait KeyValueMatchers {
    import java.util.{List => JList}
    import collection.JavaConverters._

    def containsBooleanKV(key:String, expectedValue:Boolean):Matcher[JList[KeyValue]] = new Matcher[JList[KeyValue]] {
      def apply(left: JList[KeyValue]) = {
        left.asScala.find(_.getKey.equals(key))
          .map(_.getValue.getBoolValue)
          .map(value => compare(key, expectedValue, value))
          .getOrElse(noSuchKey(key))
      }
    }

    def containsLongKV(key:String, expectedValue:Long):Matcher[JList[KeyValue]] = new Matcher[JList[KeyValue]] {
      def apply(left: JList[KeyValue]) = {
        left.asScala.find(_.getKey.equals(key))
          .map(_.getValue.getIntValue)
          .map(value => compare(key, expectedValue, value))
          .getOrElse(noSuchKey(key))
      }
    }

    def containStringKV(key:String, expectedValue:String):Matcher[JList[KeyValue]] = new Matcher[JList[KeyValue]] {
      def apply(left: JList[KeyValue]) = {
        left.asScala.find(_.getKey.equals(key))
          .map(_.getValue.getStringValue)
          .map(value => compare(key, expectedValue, value))
          .getOrElse(noSuchKey(key))
      }
    }

    private def compare(key:String, expected:Any, actual:Any) = MatchResult(
      actual.equals(expected),
      s"The KeyValue [$key] did not have expected value [$expected] != [$actual]",
      "The identifier is of correct value"
    )

    private def noSuchKey(key:String) = MatchResult(
      false,
      s"No such Key [$key] found",
      "..."
    )
  }
}
