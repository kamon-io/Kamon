lazy val Cassandra3xTest = config("testCas3") extend (Test)
lazy val Cassandra4xTest = config("testCas4") extend (Test)

val cassandra3xDriverVersion = "3.10.0"
val cassandra4xDriverVersion = "4.10.0"

libraryDependencies ++= Seq(
  kanelaAgent % "provided",
  scalatest % "test",
  logbackClassic % "test",
  "org.testcontainers" % "cassandra" % "1.15.3" % "test"
)

libraryDependencies ++= Seq(
  "com.datastax.cassandra" % "cassandra-driver-core" % cassandra3xDriverVersion % "provided,testCas3"
)

libraryDependencies ++= Seq(
  "com.datastax.oss" % "java-driver-core" % cassandra4xDriverVersion % "provided,testCas4",
  "com.datastax.oss" % "java-driver-query-builder" % cassandra4xDriverVersion % "provided,testCas4"
)

configs(Cassandra3xTest, Cassandra4xTest)
inConfig(Cassandra3xTest)(Defaults.testSettings)
inConfig(Cassandra4xTest)(Defaults.testSettings)

Test / test := {
  (Cassandra3xTest / test).value
  (Cassandra4xTest / test).value
}
