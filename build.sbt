//onLoad in Global ~= (_ andThen ("project kamonApmReporter" :: _))
resolvers += Resolver.bintrayRepo("kamon-io", "snapshots")

lazy val kamonCore = "io.kamon" %% "kamon-core" % "2.0.0-RC1"

lazy val root = project
  .settings(name := "kamon-apm-reporter")
  .aggregate(reporter, publishing)

lazy val reporter = (project in file("kamon-apm-reporter"))
  .enablePlugins(AssemblyPlugin)
  .settings(
    skip in publish := true,
    scalaVersion := "2.12.8",
    crossScalaVersions := Seq("2.11.12", "2.12.8"),
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(
      includeScala = false,
      includeDependency = true,
      includeBin = true
    ),
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", xs @ _*)  => MergeStrategy.discard
      case _                              => MergeStrategy.first
    },

    assemblyShadeRules in assembly := Seq(
       ShadeRule.rename("fastparse.**"            -> "shaded.@0").inAll
      ,ShadeRule.rename("fansi.**"                -> "shaded.@0").inAll
      ,ShadeRule.rename("sourcecode.**"           -> "shaded.@0").inAll
      ,ShadeRule.rename("com.google.protobuf.**"  -> "shaded.@0").inAll
      ,ShadeRule.rename("google.protobuf.**"      -> "shaded.@0").inAll
      ,ShadeRule.rename("okhttp3.**"              -> "shaded.@0").inAll
      ,ShadeRule.rename("okio.**"                 -> "shaded.@0").inAll
    ),

    libraryDependencies ++= Seq(
      kamonCore % "provided",
      "com.google.protobuf"   % "protobuf-java" % "3.8.0",
      "com.squareup.okhttp3"  % "okhttp"        % "3.9.1",

      "ch.qos.logback"    %  "logback-classic"  % "1.2.3" % "test",
      "org.scalatest"     %% "scalatest"        % "3.0.4" % "test",
      "com.typesafe.akka" %% "akka-http"        % "10.0.10" % "test",
      "com.typesafe.akka" %% "akka-testkit"     % "2.4.19" % "test"
    )
  )

lazy val publishing = project
  .settings(
    moduleName := "kamon-apm-reporter",
    packageBin in Compile := (assembly in (reporter, Compile)).value,
    packageSrc in Compile := (packageSrc in (reporter, Compile)).value,
    libraryDependencies += kamonCore
  )

