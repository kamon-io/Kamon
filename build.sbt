scalaVersion := "2.12.8"
resolvers += Resolver.mavenLocal
resolvers += Resolver.bintrayRepo("kamon-io", "snapshots")

libraryDependencies += "io.kamon" %% "kamon-core" % "1.1.5"
libraryDependencies += "io.zipkin.reporter2" % "zipkin-reporter" % "2.7.14"
libraryDependencies += "io.zipkin.reporter2" % "zipkin-sender-okhttp3" % "2.7.14"
libraryDependencies += scalatest % "test"
