lazy val root: Project = project.in(file(".")).dependsOn(latestSbtUmbrella)
lazy val latestSbtUmbrella = RootProject(uri("git://github.com/kamon-io/kamon-sbt-umbrella.git#kamon-1.x"))
