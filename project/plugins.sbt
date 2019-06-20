lazy val root = project in file(".") dependsOn(RootProject(uri("git://github.com/kamon-io/kamon-sbt-umbrella.git#kamon-2.x")))
addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.2")
