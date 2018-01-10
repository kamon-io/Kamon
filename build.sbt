scalaVersion := "2.12.2"
resolvers += Resolver.bintrayRepo("kamon-io", "snapshots")

lazy val kamonCoreDep = "io.kamon" %% "kamon-core" % "1.0.0-RC7"

lazy val excludedPackages = Seq(
  "kamon-core"
)

lazy val depsAssembly= (project in file("."))
  .enablePlugins(AssemblyPlugin)
  .settings(
    crossScalaVersions := Seq("2.11.11", "2.12.2"),
    skip in publish := true,
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
       ShadeRule.zap("org.reactivestreams.**").inAll
      ,ShadeRule.zap("org.slf4j.**").inAll
      ,ShadeRule.rename("fastparse.**"            -> "shaded.@0").inAll
      ,ShadeRule.rename("fansi.**"                -> "shaded.@0").inAll
      ,ShadeRule.rename("sourcecode.**"           -> "shaded.@0").inAll
      ,ShadeRule.rename("com.google.protobuf.**"  -> "shaded.@0").inAll
      ,ShadeRule.rename("google.protobuf.**"      -> "shaded.@0").inAll
      ,ShadeRule.rename("com.squareup.**"      -> "shaded.@0").inAll
      ,ShadeRule.rename("okio.**"      -> "shaded.@0").inAll
      ,ShadeRule.zap("org.HdrHistogram.**").inAll
      ,ShadeRule.zap("com.typesafe.config.**").inAll
    ),

    assemblyExcludedJars in assembly := {
      val cp = (fullClasspath in assembly).value
      cp filter { file => {
        excludedPackages.exists(file.data.getName.startsWith(_))
      }}
    },

    libraryDependencies ++= Seq(
      kamonCoreDep,
      "com.google.protobuf" % "protobuf-java" % "3.4.0",
      "org.reactivestreams" % "reactive-streams" % "1.0.0",
      "org.slf4j" % "slf4j-api" % "1.7.25",
      "com.squareup.okhttp" % "okhttp" % "2.7.5",
      "org.scalatest" %% "scalatest" % "3.0.4" % Test,
      "com.typesafe.akka" %% "akka-http" % "10.0.10" % Test,
      "com.typesafe.akka" %% "akka-testkit" % "2.4.19" % Test
    )
  )

lazy val publishing = project
  .settings(
    crossScalaVersions := Seq("2.11.11", "2.12.2"),
    name := "kamino-reporter",
    libraryDependencies ++= Seq(
      kamonCoreDep,
      "org.reactivestreams" % "reactive-streams" % "1.0.0"
    ),
    packageBin in Compile := (assembly in (depsAssembly, Compile)).value
  )

