import sbt._
import sbt.Keys._
import sbt.Project.Initialize
import java.lang.Boolean.{valueOf => convertToBoolean }

object Publish {

  lazy val settings = Seq(
    crossPaths := false,
    pomExtra := kamonPomExtra,
    publishTo <<= kamonPublish,
    organization := kamonOrganization,
    credentials ++= kamonCredentials,
    pomIncludeRepository := { x => false },
    publishMavenStyle := true,
    publishArtifact in Test := false
  )

  def kamonPublish:Initialize[Option[Resolver]] = {
    if(convertToBoolean(System.getProperty("publish.to.sonatype"))) sonatypePublishRepository
    else kamonPublishRepository
  }

  def sonatypePublishRepository: Initialize[Option[Resolver]] = {
    version { v: String =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases" at nexus + "service/local/staging/deploy/maven2")
    }
  }

  def kamonPublishRepository :Initialize[Option[Resolver]] = {
    version { (v: String) =>
      if (v.trim.endsWith("SNAPSHOT"))
        Some(Resolver.sftp("Kamon Snapshots Repository", "snapshots.kamon.io", "/var/local/snapshots-repo"))
      else
        Some(Resolver.sftp("Kamon Repository", "repo.kamon.io", "/var/local/releases-repo"))
    }
  }

  def kamonOrganization: String = Option(System.getProperty("kamon.publish.organization", "kamon")).get

  def kamonCredentials: Seq[Credentials] =
    Option(System.getProperty("kamon.publish.credentials", null)) map (f => Credentials(new File(f))) toSeq

  def kamonPomExtra = {
      <url>http://kamon.io</url>
      <licenses>
        <license>
          <name>Apache 2</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        </license>
      </licenses>
      <scm>
        <url>git://github.com/kamon-io/Kamon.git</url>
        <connection>scm:git:git@github.com:kamon-io/Kamon.git</connection>
      </scm>
      <developers>
        <developer><id>ivantopo</id><name>Ivan Topolnjak</name><url>https://twitter.com/ivantopo</url></developer>
        <developer><id>dpsoft</id><name>Diego Parra</name><url>https://twitter.com/diegolparra</url></developer>
      </developers>
  }
}