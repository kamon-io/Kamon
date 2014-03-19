import sbt._
import sbt.Keys._

object Site {
  val serveSite = taskKey[Unit]("Start a embedded web server with access to the site.")
  val jekyllSource = settingKey[File]("Location of jekyll sources.")

  val siteSettings = Seq(
    jekyllSource := sourceDirectory.value / "main" / "jekyll",
    serveSite    := {
      val command = "jekyll serve --watch --trace --detach" +
        " --source " + jekyllSource.value.absolutePath +
        " --destination " + (target.value / "_site").absolutePath

      sbt.Process(command, jekyllSource.value, ("LC_ALL", "en_US.UTF-8")).run
    }
  )
}