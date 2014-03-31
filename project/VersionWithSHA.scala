import sbt.Process

object VersionWithSHA {

  private lazy val VersionWithShaRegex = """(?:\d+\.)?(?:\d+\.)?(?:\d+)-[0-9a-f]{5,40}"""

  /** Don't use this. You should use version.value instead. */
  def kamonVersionWithSHA(version: String) = version.takeWhile(_ != '-') + "-" + Process("git rev-parse HEAD").lines.head

  /** Don't use this. You should use isSnapshot.value instead. */
  def kamonIsSnapshot(version: String) = (version matches VersionWithShaRegex) || (version endsWith "-SNAPSHOT")

}