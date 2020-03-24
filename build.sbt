scalaVersion := "2.12.11"
resolvers += Resolver.mavenLocal
resolvers += Resolver.bintrayRepo("kamon-io", "snapshots")

libraryDependencies ++= Seq(
  "io.kamon" %% "kamon-core" % "2.0.5",
  "io.zipkin.reporter2" % "zipkin-reporter" % "2.7.15",
  "io.zipkin.reporter2" % "zipkin-sender-okhttp3" % "2.7.15",
  scalatest % "test"
)
