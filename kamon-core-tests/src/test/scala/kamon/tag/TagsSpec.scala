package kamon.tag

import java.util.Optional

import org.scalatest.{Matchers, WordSpec}

import scala.collection.JavaConverters.mapAsJavaMapConverter

class TagsSpec extends WordSpec with Matchers {
  import Lookups._

  "Tags" should {
    "silently drop null and unacceptable keys and/or values when constructed from the companion object builders" in {
      Tags.from(NullString, NullString).all().size shouldBe 0
      Tags.from(EmptyString, NullString).all().size shouldBe 0
      Tags.from(EmptyString, "value").all().size shouldBe 0
      Tags.from(NullString, "value").all().size shouldBe 0
      Tags.from("key", NullString).all().size shouldBe 0
      Tags.from("key", NullBoolean).all().size shouldBe 0
      Tags.from("key", NullLong).all().size shouldBe 0

      Tags.from(BadScalaTagMap).all().size shouldBe 0
      Tags.from(BadJavaTagMap).all().size shouldBe 0
    }

    "silently drop null keys and/or values when created with the .withTag, withTags or .and methods" in {
      val tags = Tags.from("initialKey", "initialValue")
        .withTag(NullString, NullString)
        .withTag(EmptyString, NullString)
        .withTag(EmptyString, "value")
        .withTag(NullString, "value")
        .withTag("key", NullString)
        .withTag("key", NullBoolean)
        .withTag("key", NullLong)
        .and(NullString, NullString)
        .and(EmptyString, NullString)
        .and(EmptyString, "value")
        .and(NullString, "value")
        .and("key", NullString)
        .and("key", NullBoolean)
        .and("key", NullLong)

      tags.all().length shouldBe 1
      tags.all().head.asInstanceOf[Tag.String].key shouldBe "initialKey"
      tags.all().head.asInstanceOf[Tag.String].value shouldBe "initialValue"
    }

    "create a properly populated instance when valid pairs are provided" in {
      Tags.from("isAwesome", true).all().size shouldBe 1
      Tags.from("name", "kamon").all().size shouldBe 1
      Tags.from("age", 5L).all().size shouldBe 1

      Tags.from(GoodScalaTagMap).all().size shouldBe 3
      Tags.from(GoodJavaTagMap).all().size shouldBe 3

      Tags.from("initial", "initial")
        .withTag("isAwesome", true)
        .withTag("name", "Kamon")
        .withTag("age", 5L)
        .and("isAvailable", true)
        .and("website", "kamon.io")
        .and("supportedPlatforms", 1L)
        .all().size shouldBe 7
    }

    "override pre-existent tags when merging with other Tags instance" in {
      val leftTags = Tags.from(GoodScalaTagMap)
      val rightTags = Tags
        .from("name", "New Kamon")
        .and("age", 42L)
        .and("isAwesome", false) // just for testing :)

      val tags = leftTags.withTags(rightTags)
      tags.get(plain("name")) shouldBe "New Kamon"
      tags.get(plainLong("age")) shouldBe 42L
      tags.get(plainBoolean("isAwesome")) shouldBe false

      val andTags = tags and leftTags
      andTags.get(plain("name")) shouldBe "Kamon"
      andTags.get(plainLong("age")) shouldBe 5L
      andTags.get(plainBoolean("isAwesome")) shouldBe true
    }

    "provide typed access to the contained pairs when looking up values" in {
      val tags = Tags.from(GoodScalaTagMap)

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
      val tags = Tags.from(Map(
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
      Tags.from(GoodScalaTagMap) shouldBe Tags.from(GoodScalaTagMap)
      Tags.from(GoodJavaTagMap) shouldBe Tags.from(GoodJavaTagMap)
    }

    "have a readable toString implementation" in {
      Tags.from(GoodScalaTagMap).toString() should include("age=5")
      Tags.from(GoodScalaTagMap).toString() should include("name=Kamon")
      Tags.from(GoodScalaTagMap).toString() should include("isAwesome=true")
    }
  }

  def matchPair(key: String, value: Any) = { tag: Tag => {
    tag match {
      case t: Tag.String  => t.key == key && t.value == value
      case t: Tag.Long    => t.key == key && t.value == value
      case t: Tag.Boolean => t.key == key && t.value == value
    }

  }}


  val NullString: java.lang.String = null
  val NullBoolean: java.lang.Boolean = NullString.asInstanceOf[java.lang.Boolean]
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
