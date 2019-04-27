import com.lightbend.sbt.javaagent.Modules
import sbt.Keys.resourceGenerators
import BundleKeys._



lazy val instrumentationModules: Seq[ModuleID] = Seq(
//  "io.kamon" %% "kamon-scala-future"      % "2.0.0-209f237bcddf4a9c3de2ad91836b6a5d4f6ad3e6",
//  "io.kamon" %% "kamon-twitter-future"    % "2.0.0-209f237bcddf4a9c3de2ad91836b6a5d4f6ad3e6",
//  "io.kamon" %% "kamon-scalaz-future"     % "2.0.0-209f237bcddf4a9c3de2ad91836b6a5d4f6ad3e6",
  "io.kamon" %% "kamon-executors" % "2.0.0-16aace5b1de0ff1206c39ffebd6085e7997e206a"
)

val versionSettings = Seq(
  kamonCoreVersion := "2.0.0-M4",
  kanelaAgentVersion := "1.0.0-M2",
  instrumentationCommonVersion := "2.0.0-6432ad4395a3b2b6c5f03895b5313b8c80f9ead5"
)

lazy val kamonBundle = project
   .settings(noPublishing: _*)
   .aggregate(bundle, bundlePublishing)

val bundle = (project in file("."))
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(AssemblyPlugin)
  .settings(versionSettings: _*)
  .settings(
    skip in publish := true,
    resolvers += Resolver.mavenLocal,
    buildInfoPackage := "kamon.bundle",
    buildInfoKeys := Seq[BuildInfoKey](kanelaAgentJarName),
    kanelaAgentModule := "io.kamon" % "kanela-agent" % kanelaAgentVersion.value % "provided",
    kanelaAgentJar := update.value.matching(Modules.exactFilter(kanelaAgentModule.value)).head,
    kanelaAgentJarName := kanelaAgentJar.value.getName,
    resourceGenerators in Compile += Def.task(Seq(kanelaAgentJar.value)).taskValue,
    kamonCoreExclusion := ExclusionRule(organization = "io.kamon", name = s"kamon-core_${scalaBinaryVersion.value}"),
    bundleDependencies := Seq(
      kanelaAgentModule.value,
      "io.kamon"      %% "kamon-status-page"            % kamonCoreVersion.value excludeAll(kamonCoreExclusion.value),
      "io.kamon"      %% "kamon-instrumentation-common" % instrumentationCommonVersion.value excludeAll(kamonCoreExclusion.value),
      "net.bytebuddy" %  "byte-buddy-agent"             % "1.9.12",
    ),
    libraryDependencies ++= bundleDependencies.value ++ instrumentationModules.map(_.excludeAll(kamonCoreExclusion.value)),
    packageBin in Compile := assembly.value,
    assembleArtifact in assemblyPackageScala := false,
    assemblyShadeRules in assembly := Seq(
      ShadeRule.zap("**module-info").inAll,
      ShadeRule.rename("net.bytebuddy.agent.**" -> "kamon.lib.@0").inAll
    ),
    assemblyMergeStrategy in assembly := {
      case "reference.conf" => MergeStrategy.concat
      case anyOther         => (assemblyMergeStrategy in assembly).value(anyOther)
    }
  )

lazy val bundlePublishing = project
  .settings(versionSettings: _*)
  .settings(
    moduleName := "kamon-bundle",
    bintrayPackage := "kamon-bundle",
    packageBin in Compile := (packageBin in (bundle, Compile)).value,
    libraryDependencies ++= Seq(
      "io.kamon" %% "kamon-core" % kamonCoreVersion.value
    )
  )
