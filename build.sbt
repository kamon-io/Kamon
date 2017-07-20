val kamonCore = "io.kamon" %% "kamon-core" % "1.0.0-RC1-450978b92bc968bfdb9c6470028ad30586433609"
val latestLogbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.3"

name := "kamon-logback"
libraryDependencies ++=
  compileScope(kamonCore, latestLogbackClassic) ++
  testScope(scalatest)
