resolvers += Resolver.bintrayIvyRepo("kamon-io", "sbt-plugins")

lazy val root = project in file(".") dependsOn(RootProject(uri("git://github.com/kamon-io/kamon-sbt-umbrella.git#kamon-2.x")))
