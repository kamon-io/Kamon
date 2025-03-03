package kanela.agent

import java.net.URL
import java.io.File
import java.io.PrintWriter
import scala.util.Using
import java.util.Collections
import java.util.Enumeration
import java.util.Optional

class ConfigurationSpec extends munit.FunSuite {

  test("should fail to load when there is no configuration") {
    val loader = classLoaderWithConfig("")
    intercept[RuntimeException] {
      Configuration.createFrom(loader)
    }
  }

  test("should load basic configuration settings") {
    val loader = classLoaderWithConfig(
      """
        |
        |kanela {
        |  log-level = "INFO"
        |  debug-mode = off
        |}
        |""".stripMargin
    )

    val config = Configuration.createFrom(loader)
    assertEquals(config.agent().logLevel(), "INFO")
    assertEquals(config.agent().debug(), false)
  }

  test("should load a bare minimum module configuration") {
    val loader = classLoaderWithConfig(
      """
        |
        |kanela {
        |  log-level = "INFO"
        |  debug-mode = off
        |
        |  modules {
        |    tester {
        |      name = "tester module"
        |      instrumentations = [ "some.type.Instrumentation" ]
        |      within = [ "some.type.*" ]
        |    }
        |  }
        |}
        |""".stripMargin
    )

    val config = Configuration.createFrom(loader)
    val firstModule = config.modules().get(0)
    assertEquals(firstModule.name(), "tester module")
    assertEquals(firstModule.description(), Optional.empty[String]())
    assertEquals(firstModule.enabled(), true)
    assertEquals(firstModule.order(), 1)
  }

  test("sohuld load all module configuration settings") {
    val loader = classLoaderWithConfig(
      """
        |
        |kanela {
        |  log-level = "INFO"
        |  debug-mode = off
        |
        |  modules {
        |    tester {
        |      name = "tester module"
        |      description = "This does something"
        |      enabled = no
        |      order = 42
        |      instrumentations = [ "some.type.Instrumentation" ]
        |      within = [ "some.type.*" ]
        |      exclude = [ "some.other.type.*" ]
        |    }
        |  }
        |}
        |""".stripMargin
    )

    val config = Configuration.createFrom(loader)
    val firstModule = config.modules().get(0)
    assertEquals(firstModule.name(), "tester module")
    assertEquals(firstModule.description(), Optional.of("This does something"))
    assertEquals(firstModule.enabled(), false)
    assertEquals(firstModule.order(), 42)
    assertEquals(firstModule.instrumentations(), java.util.List.of("some.type.Instrumentation"))
    assertEquals(firstModule.withinPackages(), java.util.List.of("some.type.*"))
    assertEquals(firstModule.excludePackages(), Optional.of(java.util.List.of("some.other.type.*")))
  }

  def classLoaderWithConfig(reference: String): ClassLoader = new ClassLoader {
    val tempReferenceFile = File.createTempFile("reference", "conf")
    val referenceConfURL = tempReferenceFile.toURI.toURL
    tempReferenceFile.deleteOnExit()

    Using.resource(new PrintWriter(tempReferenceFile)) { writer =>
      writer.write(reference)
    }

    override def getResource(name: String): URL = {
      if (name == "reference.conf") referenceConfURL
      else super.getResource(name)
    }

    override def getResources(name: String): Enumeration[URL] = {
      if (name == "reference.conf")
        Collections.enumeration(Collections.singletonList(referenceConfURL))
      else
        super.getResources(name)
    }
  }
}
