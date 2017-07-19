scalaVersion := "2.12.2"
crossScalaVersions := Seq("2.11.11", "2.12.2")
resolvers += Resolver.mavenLocal
resolvers += Resolver.bintrayRepo("kamon-io", "snapshots")
libraryDependencies += "com.uber.jaeger" % "jaeger-thrift" % "0.18.0"
libraryDependencies += "io.kamon" %% "kamon-core" % "1.0.0-RC1-5ee1b830aff118dc264399ddfb92c1f2b1f51a85"
