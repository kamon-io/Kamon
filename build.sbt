scalaVersion := "2.12.2"
version := "0.1.3-SNAPSHOT"
resolvers += Resolver.mavenLocal
resolvers += Resolver.bintrayRepo("kamon-io", "snapshots")
libraryDependencies += "com.uber.jaeger" % "jaeger-thrift" % "0.18.0"
libraryDependencies += "io.kamon" %% "kamon-core" % "1.0.0-RC1-7aeeedad6f6684f8aae018fbf433557b2a587172"

