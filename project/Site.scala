import sbt._
import sbt.Keys._
import com.typesafe.sbt.site.JekyllSupport._
import com.typesafe.sbt.SbtSite._

object Site {
  val siteSettings = site.settings ++ site.jekyllSupport() ++
    inConfig(Jekyll)(Seq(
      RequiredGems  :=  Map("jekyll" -> "1.3.0", "liquid" -> "2.5.4"),
          mappings <<=  (sourceDirectory, target, includeFilter, CheckGems, streams) map {
                          (src, t, inc, _, s) => buildJekyll(src, t, inc, s) } ))

  def buildJekyll(src: File, target: File, inc: FileFilter, s: TaskStreams): Seq[(File, String)] = {
    // Run Jekyll
    sbt.Process(Seq("jekyll", "build", "--destination", target.getAbsolutePath), Some(src)) ! s.log match {
      case 0 => ()
      case n => sys.error("Could not run jekyll, error: " + n)
    }
    // Figure out what was generated.
    for {
      (file, name) <- (target ** inc x relativeTo(target))
    } yield file -> name
  }
}