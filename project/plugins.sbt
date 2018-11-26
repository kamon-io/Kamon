lazy val root: Project = project.in(file(".")).dependsOn(v0_0_15_SbtUmbrella)
lazy val v0_0_15_SbtUmbrella = uri("git://github.com/kamon-io/kamon-sbt-umbrella.git#v0.0.15")
