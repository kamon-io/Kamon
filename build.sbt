onLoad in Global ~= (_ andThen ("project kamonApmReporter" :: _))
resolvers += Resolver.bintrayRepo("kamon-io", "snapshots")

lazy val kamonCore = "io.kamon" %% "kamon-core" % "2.0.0-M5"

lazy val kamonApmReporter = project
  .settings(name := "kamon-apm-reporter")
  .aggregate(reporter, publishing)

lazy val reporter = (project in file("."))
  .enablePlugins(AssemblyPlugin)
  .settings(
    skip in publish := true,
    name := "kamon-apm-reporter",
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

      "ch.qos.logback"    %  "logback-classic"  % "1.2.3" % Test,
      "org.scalatest"     %% "scalatest"        % "3.0.4" % Test,
      "com.typesafe.akka" %% "akka-http"        % "10.0.10" % Test,
      "com.typesafe.akka" %% "akka-testkit"     % "2.4.19" % Test
    )
  )

lazy val publishing = project
  .settings(
    name := (name in (reporter, Compile)).value,
    scalaVersion := (scalaVersion in assembly).value,
    crossScalaVersions := (crossScalaVersions in assembly).value,
    packageBin in Compile := (assembly in (reporter, Compile)).value,
    packageSrc in Compile := (packageSrc in (reporter, Compile)).value,
    libraryDependencies += kamonCore
  )

