//<groupId>de.flapdoodle.embed</groupId>
//  <artifactId>de.flapdoodle.embed.mongo</artifactId>
//  <version>2.2.1-SNAPSHOT</version>

val kamonCore         = "io.kamon"            %%  "kamon-core"                      % "2.0.1"
val kamonTestkit      = "io.kamon"            %%  "kamon-testkit"                   % "2.0.1"
val kamonCommon       = "io.kamon"            %%  "kamon-instrumentation-common"    % "2.0.0"
val kanela            = "io.kamon"            %   "kanela-agent"                    % "1.0.3-SNAPSHOT"
val mongoSyncDriver   = "org.mongodb"         %   "mongodb-driver-sync"             % "3.11.0"
val mongoScalaDriver  = "org.mongodb.scala"   %%  "mongo-scala-driver"              % "2.7.0"
val mongoDriver       = "org.mongodb"         %   "mongodb-driver-reactivestreams"  % "1.12.0"
val embeddedMongo     = "de.flapdoodle.embed" %   "de.flapdoodle.embed.mongo"       % "2.2.0"


resolvers += Resolver.mavenLocal
kanelaAgentVersion := "1.0.3-SNAPSHOT"

lazy val root = Project("kamon-mongo", file("."))
  .settings(noPublishing: _*)
  .settings(crossScalaVersions := Nil)
  .aggregate(instrumentation)


lazy val instrumentation = Project("instrumentation", file("instrumentation"))
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    name := "kamon-mongo",
    bintrayPackage := "kamon-mongo",
    moduleName := "kamon-mongo",
    scalaVersion := "2.13.1",
    resolvers += Resolver.mavenLocal,
    kanelaAgentVersion := "1.0.3-SNAPSHOT",
    crossScalaVersions := Seq("2.11.12", "2.12.8", "2.13.1"),
    libraryDependencies ++=
      compileScope(kamonCore, kamonCommon) ++
      providedScope(kanela, mongoDriver, mongoSyncDriver, mongoScalaDriver) ++
      testScope(kamonTestkit, embeddedMongo, scalatest) ++
      testScope(
        "io.kamon" %% "kamon-bundle" % "2.0.2",
        "io.kamon" %% "kamon-apm-reporter" % "2.0.0"
      )

  )
