import sbt.librarymanagement.syntax.ExclusionRule
import sbt.{ ModuleID, settingKey, taskKey}

object BundleKeys {
  val kamonCoreVersion = settingKey[String]("Version used for kamon-core and the status page dependencies")
  val instrumentationCommonVersion = settingKey[String]("More Versions")
  val kanelaAgentModule = settingKey[ModuleID]("Dependency on the Kanela agent")
  val kanelaAgentJarName = taskKey[String]("Name of the embedded kanela jar")
  val bundleDependencies = settingKey[Seq[ModuleID]]("Dependencies")
  val kamonCoreExclusion = settingKey[ExclusionRule]("excludes core")

}
