val kamonCore = "io.kamon" %% "kamon-core" % "1.0.0-RC1-7361fde5f06692a0e1b83d53756bb536627f2d02"
val kamonTestkit = "io.kamon" %% "kamon-testkit" % "1.0.0-RC1-7361fde5f06692a0e1b83d53756bb536627f2d02"
val latestLogbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.3"

name := "kamon-logback"
libraryDependencies ++=
  compileScope(kamonCore, latestLogbackClassic) ++
  testScope(kamonTestkit, scalatest)
