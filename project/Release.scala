import com.typesafe.sbt.pgp._
import sbt._
import sbt.Keys._
import sbtrelease.ReleasePlugin._
import sbtrelease.ReleaseStateTransformations._
import sbtrelease.ReleaseStep
import sbtrelease.Utilities._
import xerial.sbt.Sonatype._

object Release {

  def settings = Seq.empty ++
    releaseSettings ++
    Seq(
      ReleaseKeys.releaseProcess := Seq[ReleaseStep](
        checkSnapshotDependencies,
        inquireVersions,
        runClean,
        runTest,
        setReleaseVersion,
        commitReleaseVersion, // .copy(check = identity), // FIX 0: to skip "all changes committed" precondition
        tagRelease,
        publishSignedArtifacts, // FIX 1: publish signed. Otherwise sonatype won't sync artifact to maven central
        setNextVersion,
        commitNextVersion,
        pushChanges,
        refreshVersionWithSHA // FIX 2: update "version" by replacing the "-SNAPSHOT" with "-WHATEVER_COMMIT_SHA"
      )
    ) ++
    sonatypeSettings ++
    Seq(
      // sbt-sonatype overrides publishTo. So we need to restore kamon repo declaration for snapshots
      publishTo := { if (isSnapshot.value) Publish.kamonRepo else publishTo.value }
    )


  def kamonSonatypeCredentials =
    Credentials.toDirect(Credentials(Path.userHome / ".ivy2" / "kamon-credentials-sonatype.properties"))

  /**
   * Hijacked from [[sbtrelease.ReleaseStateTransformations.publishArtifacts]]
   */
  lazy val publishSignedArtifacts = ReleaseStep(
    action = { st: State =>
      val extracted = st.extract
      val ref = extracted.get(thisProjectRef)
      extracted.runAggregated(PgpKeys.publishSigned in Global in ref, st)
    },
    check = st => {
      // getPublishTo fails if no publish repository is set up.
      val ex = st.extract
      val ref = ex.get(thisProjectRef)
      Classpaths.getPublishTo(ex.get(publishTo in Global in ref))
      st
    },
    enableCrossBuild = true
  )

  lazy val refreshVersionWithSHA = ReleaseStep(st => {
    reapply(Seq(
      version in ThisBuild := VersionWithSHA.kamonVersionWithSHA(st.extract.get(version))
    ), st)
  })

}