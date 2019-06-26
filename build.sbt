scalaVersion := "2.13.0"
resolvers += Resolver.mavenLocal
libraryDependencies += "io.jaegertracing" % "jaeger-thrift" % "0.35.5"
libraryDependencies += "io.kamon" %% "kamon-core" % "2.0.0-RC1"

crossScalaVersions := Seq("2.11.12", "2.12.7", "2.13.0")
