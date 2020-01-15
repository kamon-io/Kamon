scalaVersion := "2.13.1"
resolvers += Resolver.mavenLocal
libraryDependencies += "io.jaegertracing" % "jaeger-thrift" % "1.1.0"
libraryDependencies += "io.kamon" %% "kamon-core" % "2.0.4"

crossScalaVersions := Seq("2.11.12", "2.12.10", "2.13.1")
