import sbt._
import sbt.Keys._
import com.typesafe.sbt.SbtAspectj.{ Aspectj, defaultAspectjSettings }
import com.typesafe.sbt.SbtAspectj.AspectjKeys.{ aspectjVersion, compileOnly, lintProperties, weaverOptions }


object AspectJ {

  lazy val aspectJSettings = inConfig(Aspectj)(defaultAspectjSettings) ++ aspectjDependencySettings ++ Seq(
   aspectjVersion in Aspectj    :=  Dependencies.aspectjVersion,
      compileOnly in Aspectj    :=  true,
                fork in Test    :=  true,
         javaOptions in Test  <++=  weaverOptions in Aspectj,
          javaOptions in run  <++=  weaverOptions in Aspectj,
   lintProperties in Aspectj    +=  "invalidAbsoluteTypeName = ignore"
  )

  def aspectjDependencySettings = Seq(
    ivyConfigurations += Aspectj,
    libraryDependencies <++= (aspectjVersion in Aspectj) { version => Seq(
      "org.aspectj" % "aspectjtools" % version % Aspectj.name,
      "org.aspectj" % "aspectjweaver" % version % Aspectj.name,
      "org.aspectj" % "aspectjrt" % version % Aspectj.name
    )}
  )
}