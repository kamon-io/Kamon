lazy val root: Project = project.in(file(".")).dependsOn(latestSbtUmbrella)
lazy val latestSbtUmbrella = ProjectRef(uri("git://github.com/kamon-io/kamon-sbt-umbrella.git#kamon-2.x"), "kamon-sbt-umbrella")

addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.4")
