package kamon.instrumentation.jdbc.utils

object Obfuscator {

	def obfuscateUrl(url: String): String = {
		val splitUrl = url.split('?')
		val urlParams = splitUrl(1)
		val splitParams = urlParams.split("&")
		val obfuscatedParams = splitParams.toList.map { p =>
			val keyValue = p.split("=")
			if (keyValue(0) == "user" || keyValue(0) == "password") {
				(keyValue(0), "XXXXXXXXXXX")
			}
			else {
				(keyValue(0), keyValue(1))
			}
		}
		val obfuscatedUrlParams = obfuscatedParams.foldLeft("") { (acc, kv) =>
			acc + "&" + kv._1 + "=" + kv._2
		}
		splitUrl(0) + "?" + obfuscatedUrlParams
	}
}
