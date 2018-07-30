scalaVersion := "2.12.6"
resolvers += Resolver.mavenLocal
resolvers += Resolver.bintrayRepo("kamon-io", "snapshots")

libraryDependencies += "io.kamon" %% "kamon-core" % "1.1.3"
libraryDependencies += "io.zipkin.reporter2" % "zipkin-reporter" % "2.7.7"
libraryDependencies += "io.zipkin.reporter2" % "zipkin-sender-okhttp3" % "2.7.7"
libraryDependencies += scalatest % "test"
