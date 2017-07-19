scalaVersion := "2.12.2"
crossScalaVersions := Seq("2.11.11", "2.12.2")
resolvers += Resolver.mavenLocal
resolvers += Resolver.bintrayRepo("kamon-io", "snapshots")
libraryDependencies += "com.uber.jaeger" % "jaeger-thrift" % "0.18.0"
libraryDependencies += "io.kamon" %% "kamon-core" % "1.0.0-RC1-450978b92bc968bfdb9c6470028ad30586433609"
