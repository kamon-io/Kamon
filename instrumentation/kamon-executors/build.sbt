import sbt.Tests.{Group, SubProcess}

Test / testGrouping := groupByExperimentalExecutorTests((Test / definedTests).value, kanelaAgentJar.value)

def groupByExperimentalExecutorTests(tests: Seq[TestDefinition], kanelaJar: File): Seq[Group] = {
  val (stable, experimental) =
    tests.partition(t => t.name != "kamon.instrumentation.executor.CaptureContextOnSubmitInstrumentationSpec")

  val stableGroup = Group(
    "stableTests",
    stable,
    SubProcess(
      ForkOptions().withRunJVMOptions(Vector(
        "-javaagent:" + kanelaJar.toString
      ))
    )
  )

  val experimentalGroup = Group(
    "experimentalTests",
    experimental,
    SubProcess(
      ForkOptions().withRunJVMOptions(Vector(
        "-javaagent:" + kanelaJar.toString,
        "-Dkanela.modules.executor-service.enabled=false",
        "-Dkanela.modules.executor-service-capture-on-submit.enabled=true"
      ))
    )
  )

  Seq(stableGroup, experimentalGroup)
}
