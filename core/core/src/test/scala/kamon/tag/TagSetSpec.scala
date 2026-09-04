package kamon.tag

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.Optional
import scala.collection.JavaConverters.mapAsJavaMapConverter

class TagSetSpec extends AnyWordSpec with Matchers {
  import Lookups._

  "Tags" should {
    "silently drop null and unacceptable keys and/or values when constructed from the companion object builders" in {
      TagSet.of(NullString, NullString).all().size shouldBe 0
      TagSet.of(EmptyString, NullString).all().size shouldBe 0
      TagSet.of(EmptyString, "value").all().size shouldBe 0
      TagSet.of(NullString, "value").all().size shouldBe 0
      TagSet.of("key", NullString).all().size shouldBe 0
      TagSet.of("key", NullBoolean).all().size shouldBe 0
      TagSet.of("key", NullLong).all().size shouldBe 0

      TagSet.from(BadScalaTagMap).all().size shouldBe 0
      TagSet.from(BadJavaTagMap).all().size shouldBe 0
    }

    "silently drop null keys and/or values when created with the .withTag or .withTags methods" in {
      val tags = TagSet.of("initialKey", "initialValue")
        .withTag(NullString, NullString)
        .withTag(EmptyString, NullString)
        .withTag(EmptyString, "value")
        .withTag(NullString, "value")
        .withTag("key", NullString)
        .withTag("key", NullBoolean)
        .withTag("key", NullLong)
        .withTag(NullString, NullString)
        .withTag(EmptyString, NullString)
        .withTag(EmptyString, "value")
        .withTag(NullString, "value")
        .withTag("key", NullString)
        .withTag("key", NullBoolean)
        .withTag("key", NullLong)

      tags.all().length shouldBe 1
      tags.all().head.asInstanceOf[Tag.String].key shouldBe "initialKey"
      tags.all().head.asInstanceOf[Tag.String].value shouldBe "initialValue"
    }

    "create a properly populated instance when valid pairs are provided" in {
      TagSet.of("isAwesome", true).all().size shouldBe 1
      TagSet.of("name", "kamon").all().size shouldBe 1
      TagSet.of("age", 5L).all().size shouldBe 1

      TagSet.from(GoodScalaTagMap).all().size shouldBe 3
      TagSet.from(GoodJavaTagMap).all().size shouldBe 3

      TagSet.of("initial", "initial")
        .withTag("isAwesome", true)
        .withTag("name", "Kamon")
        .withTag("age", 5L)
        .withTag("isAvailable", true)
        .withTag("website", "kamon.io")
        .withTag("supportedPlatforms", 1L)
        .all().size shouldBe 7
    }

    "override pre-existent tags when merging with other Tags instance" in {
      val leftTags = TagSet.from(GoodScalaTagMap)
      val rightTags = TagSet
        .of("name", "New Kamon")
        .withTag("age", 42L)
        .withTag("isAwesome", false) // just for testing :)

      val tags = leftTags.withTags(rightTags)
      tags.get(plain("name")) shouldBe "New Kamon"
      tags.get(plainLong("age")) shouldBe 42L
      tags.get(plainBoolean("isAwesome")) shouldBe false

      val andTags = tags withTags leftTags
      andTags.get(plain("name")) shouldBe "Kamon"
      andTags.get(plainLong("age")) shouldBe 5L
      andTags.get(plainBoolean("isAwesome")) shouldBe true
    }

    "provide typed access to the contained pairs when looking up values" in {
      val tags = TagSet.from(GoodScalaTagMap)

      tags.get(plain("name")) shouldBe "Kamon"
      tags.get(plain("none")) shouldBe null
      tags.get(option("name")) shouldBe Option("Kamon")
      tags.get(option("none")) shouldBe None
      tags.get(optional("name")) shouldBe Optional.of("Kamon")
      tags.get(optional("none")) shouldBe Optional.empty()

      tags.get(plainLong("age")) shouldBe 5L
      tags.get(plainLong("nil")) shouldBe null
      tags.get(longOption("age")) shouldBe Option(5L)
      tags.get(longOption("nil")) shouldBe None
      tags.get(longOptional("age")) shouldBe Optional.of(5L)
      tags.get(longOptional("nil")) shouldBe Optional.empty()

      tags.get(plainBoolean("isAwesome")) shouldBe true
      tags.get(plainBoolean("isUnknown")) shouldBe null
      tags.get(booleanOption("isAwesome")) shouldBe Some(true)
      tags.get(booleanOption("isUnknown")) shouldBe None
      tags.get(booleanOptional("isAwesome")) shouldBe Optional.of(true)
      tags.get(booleanOptional("isUnknown")) shouldBe Optional.empty()

      tags.get(coerce("age")) shouldBe "5"
      tags.get(coerce("isAwesome")) shouldBe "true"
      tags.get(coerce("unknown")) shouldBe "unknown"
    }

    "allow iterating over all contained tags" in {
      val tags = TagSet.from(Map(
        "age" -> 5L,
        "name" -> "Kamon",
        "isAwesome" -> true,
        "hasTracing" -> true,
        "website" -> "kamon.io",
        "luckyNumber" -> 7L
      ))

      tags.iterator().length shouldBe 6
      tags.iterator().find(matchPair("age", 5L)) shouldBe defined
      tags.iterator().find(matchPair("luckyNumber", 7L)) shouldBe defined
      tags.iterator().find(matchPair("hasTracing", true)) shouldBe defined
      tags.iterator().find(matchPair("isAwesome", true)) shouldBe defined
      tags.iterator().find(matchPair("website", "kamon.io")) shouldBe defined
      tags.iterator().find(matchPair("name", "Kamon")) shouldBe defined
    }

    "be equal to other Tags instance with the same tags" in {
      TagSet.from(GoodScalaTagMap) shouldBe TagSet.from(GoodScalaTagMap)
      TagSet.from(GoodJavaTagMap) shouldBe TagSet.from(GoodJavaTagMap)
    }

    "have a readable toString implementation" in {
      TagSet.from(GoodScalaTagMap).toString() should include("age=5")
      TagSet.from(GoodScalaTagMap).toString() should include("name=Kamon")
      TagSet.from(GoodScalaTagMap).toString() should include("isAwesome=true")
    }

    "allow creating them from a builder instance" in {
      val tags = TagSet.builder()
        .add("age", 5L)
        .add("name", "Kamon")
        .add("isAwesome", true)
        .add("hasTracing", true)
        .add("website", "wrong.io")
        .add("website", "wrong.io")
        .add("website", "kamon.io")
        .add("luckyNumber", 7L)
        .add("luckyNumber", 7L)
        .build()

      tags.get(plain("name")) shouldBe "Kamon"
      tags.get(plain("website")) shouldBe "kamon.io"
      tags.get(plainLong("age")) shouldBe 5L
      tags.get(plainLong("luckyNumber")) shouldBe 7L
      tags.get(plainBoolean("isAwesome")) shouldBe true
      tags.get(plainBoolean("hasTracing")) shouldBe true

    }

    "allow removing keys" in {
      val tags = TagSet.from(Map(
        "age" -> 5L,
        "name" -> "Kamon",
        "isAwesome" -> true,
        "hasTracing" -> true,
        "website" -> "kamon.io",
        "luckyNumber" -> 7L
      ))

      tags.without("name").get(option("name")) shouldBe empty
      tags.without("website").get(plain("name")) shouldBe "Kamon"
    }
  }

  def matchPair(key: String, value: Any) = { tag: Tag =>
    {
      tag match {
        case t: Tag.String  => t.key == key && t.value == value
        case t: Tag.Long    => t.key == key && t.value == value
        case t: Tag.Boolean => t.key == key && t.value == value
      }

    }
  }

  val NullString: java.lang.String = null
  val NullBoolean: java.lang.Boolean = null
  val NullLong: java.lang.Long = null
  val EmptyString: java.lang.String = ""

  val GoodScalaTagMap: Map[String, Any] = Map(
    "age" -> 5L,
    "name" -> "Kamon",
    "isAwesome" -> true
  )

  val BadScalaTagMap: Map[String, Any] = Map(
    NullString -> NullString,
    EmptyString -> NullString,
    NullString -> NullString,
    EmptyString -> NullString,
    EmptyString -> "value",
    NullString -> "value",
    "key" -> NullString,
    "key" -> NullBoolean,
    "key" -> NullLong
  )

  val GoodJavaTagMap = GoodScalaTagMap.asJava
  val BadJavaTagMap = BadScalaTagMap.asJava

}
