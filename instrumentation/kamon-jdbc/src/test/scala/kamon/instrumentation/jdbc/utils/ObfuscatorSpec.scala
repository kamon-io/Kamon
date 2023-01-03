package kamon.instrumentation.jdbc.utils

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

class ObfuscatorSpec extends AnyFlatSpec with should.Matchers {

	"An url which contains a parameter named password" should "be obfucated" in {
		val url = "jdbc:postgresql://localhost:5432/local_raas?password=123-abc-123&ApplicationName=ALL"
		val obfuscatedUrl = Obfuscator.obfuscateUrl(url)
		obfuscatedUrl should be("jdbc:postgresql://localhost:5432/local_raas?password=XXXXXXXXXXX&ApplicationName=ALL")
	}
}
