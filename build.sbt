import com.lightbend.sbt.javaagent.Modules
import sbt.Keys.resourceGenerators
import BundleKeys._
resolvers += Resolver.mavenLocal

lazy val instrumentationModules: Seq[ModuleID] = Seq(
  "io.kamon" %% "kamon-executors"         % "2.0.0",
  "io.kamon" %% "kamon-scala-future"      % "2.0.0",
  "io.kamon" %% "kamon-akka"              % "2.0.0",
  "io.kamon" %% "kamon-akka-http"         % "2.0.0",
  "io.kamon" %% "kamon-play"              % "2.0.0",
  "io.kamon" %% "kamon-jdbc"              % "2.0.0",
  "io.kamon" %% "kamon-logback"           % "2.0.0",
  "io.kamon" %% "kamon-system-metrics"    % "2.0.0" exclude("org.slf4j", "slf4j-api")
)

val versionSettings = Seq(
  kamonCoreVersion := "2.0.0",
  kanelaAgentVersion := "1.0.0",
  instrumentationCommonVersion := "2.0.0"
)

lazy val root = Project("kamon-bundle", file("."))
   .settings(noPublishing: _*)
   .aggregate(bundle, publishing)

val bundle = (project in file("bundle"))
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(AssemblyPlugin)
  .settings(versionSettings: _*)
  .settings(
    skip in publish := true,
    moduleName := "kamon-bundle",
    resolvers += Resolver.mavenLocal,
    resolvers += Resolver.bintrayRepo("kamon-io", "releases"),
    buildInfoPackage := "kamon.bundle",
    buildInfoKeys := Seq[BuildInfoKey](kanelaAgentJarName),
    kanelaAgentModule := "io.kamon" % "kanela-agent" % kanelaAgentVersion.value % "provided",
    kanelaAgentJar := update.value.matching(Modules.exactFilter(kanelaAgentModule.value)).head,
    kanelaAgentJarName := kanelaAgentJar.value.getName,
    resourceGenerators in Compile += Def.task(Seq(kanelaAgentJar.value)).taskValue,
    kamonCoreExclusion := ExclusionRule(organization = "io.kamon", name = s"kamon-core_${scalaBinaryVersion.value}"),
    bundleDependencies := Seq(
      kanelaAgentModule.value,
      "io.kamon"      %% "kamon-status-page"            % kamonCoreVersion.value excludeAll(kamonCoreExclusion.value) changing(),
      "io.kamon"      %% "kamon-instrumentation-common" % instrumentationCommonVersion.value excludeAll(kamonCoreExclusion.value) changing(),
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

lazy val publishing = project
  .settings(versionSettings: _*)
  .settings(
    moduleName := (moduleName in (bundle, Compile)).value,
    scalaVersion := (scalaVersion in bundle).value,
    crossScalaVersions := (crossScalaVersions in bundle).value,
    packageBin in Compile := (packageBin in (bundle, Compile)).value,
    packageSrc in Compile := (packageSrc in (bundle, Compile)).value,
    bintrayPackage := "kamon-bundle",
    libraryDependencies ++= Seq(
      "io.kamon" %% "kamon-core" % kamonCoreVersion.value
    )
  )
