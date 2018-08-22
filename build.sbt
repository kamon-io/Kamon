scalaVersion := "2.12.6"
resolvers += Resolver.mavenLocal
resolvers += Resolver.bintrayRepo("kamon-io", "snapshots")
libraryDependencies += "io.jaegertracing" % "jaeger-thrift" % "0.30.0"
libraryDependencies += "io.kamon" %% "kamon-core" % "1.1.3"
