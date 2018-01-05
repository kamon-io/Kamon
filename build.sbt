scalaVersion := "2.12.2"
resolvers += Resolver.mavenLocal
resolvers += Resolver.bintrayRepo("kamon-io", "snapshots")

libraryDependencies += "io.kamon" %% "kamon-core" % "1.0.0-RC7"
libraryDependencies += "io.zipkin.reporter2" % "zipkin-reporter" % "2.2.3"
libraryDependencies += "io.zipkin.reporter2" % "zipkin-sender-okhttp3" % "2.2.3"
libraryDependencies += scalatest % "test"
