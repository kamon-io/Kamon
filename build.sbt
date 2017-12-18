scalaVersion := "2.12.2"
crossScalaVersions := Seq("2.11.11", "2.12.2")
resolvers += Resolver.mavenLocal
resolvers += Resolver.bintrayRepo("kamon-io", "snapshots")

libraryDependencies += "io.kamon" %% "kamon-core" % "1.0.0-RC7-ba6736eab2b5a229c2ba7263591c4e6c7c2db0b1"

libraryDependencies += "io.zipkin.reporter" % "zipkin-reporter" % "1.0.1"
libraryDependencies += "io.zipkin.reporter" % "zipkin-sender-okhttp3" % "1.0.1"
