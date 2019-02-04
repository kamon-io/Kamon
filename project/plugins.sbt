lazy val root: Project = project.in(file(".")).dependsOn(RootProject(latestSbtUmbrella))
lazy val latestSbtUmbrella = uri("git://github.com/kamon-io/kamon-sbt-umbrella.git")

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.2")
