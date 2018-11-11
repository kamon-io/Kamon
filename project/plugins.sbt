lazy val root: Project = project.in(file(".")).dependsOn(latestSbtUmbrella)
lazy val latestSbtUmbrella = uri("git://github.com/kamon-io/kamon-sbt-umbrella.git#v0.0.15")

addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.3")
