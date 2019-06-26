lazy val root: Project = project in file(".") dependsOn(RootProject(uri("git://github.com/kamon-io/kamon-sbt-umbrella.git")))
