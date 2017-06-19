scalaVersion := "2.12.2"
version := "0.1.1-SNAPSHOT"
resolvers += Resolver.mavenLocal
resolvers += Resolver.bintrayRepo("kamon-io", "snapshots")
libraryDependencies += "com.uber.jaeger" % "jaeger-thrift" % "0.18.0"
libraryDependencies += "io.kamon" %% "kamon-core" % "1.0.0-alpha1-197e2ac405e7edb1628f9319091bc8de7429e4c0"

