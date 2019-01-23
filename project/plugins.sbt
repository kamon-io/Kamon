lazy val root: Project = project in file(".") dependsOn(RootProject(uri("git://github.com/kamon-io/kamon-sbt-umbrella.git")))

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.9.0")