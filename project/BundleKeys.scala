import sbt.librarymanagement.syntax.ExclusionRule
import sbt.{ModuleID, settingKey, taskKey}

object BundleKeys {
  val kanelaAgentModule = settingKey[ModuleID]("Dependency on the Kanela agent")
  val kanelaAgentJarName = taskKey[String]("Name of the embedded kanela jar")
  val bundleDependencies = settingKey[Seq[ModuleID]]("Dependencies")
  val kamonCoreExclusion = settingKey[ExclusionRule]("excludes core")

}
