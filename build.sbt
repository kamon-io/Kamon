scalaVersion := "2.12.6"
resolvers += Resolver.mavenLocal
resolvers += Resolver.bintrayRepo("kamon-io", "snapshots")
libraryDependencies += ("io.jaegertracing" % "jaeger-thrift" % "0.34.0" classifier "shadow")
  .exclude("io.jaegertracing", "jaeger-core")
  .exclude("com.google.code.gson", "gson")
    .exclude("io.opentracing", "opentracing-api")
    .exclude("com.squareup.okhttp3", "okhttp")
    .exclude("org.apache.thrift", "libthrift")
libraryDependencies += "io.kamon" %% "kamon-core" % "2.0.0-M4"
