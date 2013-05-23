import sbt._
import sbt.Keys._
import com.typesafe.sbt.SbtAspectj
import com.typesafe.sbt.SbtAspectj.Aspectj
import com.typesafe.sbt.SbtAspectj.AspectjKeys._


object AspectJ {

  lazy val aspectJSettings = SbtAspectj.aspectjSettings ++ Seq(
           fork in Test   := true,
    javaOptions in Test   <++= weaverOptions in Aspectj
  )
}