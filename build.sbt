val kamonCore = "io.kamon" %% "kamon-core" % "1.0.0-RC1-1d0548cb8281202738d8d48cbe9cdd62cf209e21"

name := "kamon-logback"
libraryDependencies ++=
  compileScope(kamonCore, logbackClassic) ++
  testScope(scalatest)
