scalaVersion := "2.11.8"

name := "kamon-graphite"
organization := "io.kamon"

libraryDependencies ++= Seq(
  "io.kamon" %% "kamon-core" % "0.6.2"
)


publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

publishMavenStyle := true
pomIncludeRepository := { _ => false }

pomExtra := (
  <url>http://kamon.io</url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
    <scm>
      <url>git://github.com/kamon-io/kamon-graphite</url>
      <connection>scm:git:git@github.com:kamon-io/kamon-graphite.git</connection>
    </scm>
    <developers>
      <developer><id>ivantopo</id><name>Ivan Topolnjak</name><url>https://github.com/ivantopo</url></developer>
    </developers>
  )


import ReleaseTransformations._
import com.typesafe.sbt.pgp.PgpKeys.publishSigned

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,              // : ReleaseStep
  inquireVersions,                        // : ReleaseStep
  runTest,                                // : ReleaseStep
  setReleaseVersion,                      // : ReleaseStep
  commitReleaseVersion,                   // : ReleaseStep, performs the initial git checks
  tagRelease,                             // : ReleaseStep
  releaseStepTask(publishSigned),         // : ReleaseStep, checks whether `publishTo` is properly set up
  setNextVersion,                         // : ReleaseStep
  commitNextVersion                       // : ReleaseStep
)