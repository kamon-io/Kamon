lazy val root: Project = project.in(file(".")).dependsOn(latestSbtUmbrella)
lazy val latestSbtUmbrella = uri("https://github.com/kamon-io/kamon-sbt-umbrella.git")

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.2")
