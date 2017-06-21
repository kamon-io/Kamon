scalaVersion := "2.12.2"
crossScalaVersions := Seq("2.11.11", "2.12.2")
resolvers += Resolver.mavenLocal
resolvers += Resolver.bintrayRepo("kamon-io", "snapshots")
libraryDependencies += "com.uber.jaeger" % "jaeger-thrift" % "0.18.0"
libraryDependencies += "io.kamon" %% "kamon-core" % "1.0.0-RC1-1d0548cb8281202738d8d48cbe9cdd62cf209e21"
