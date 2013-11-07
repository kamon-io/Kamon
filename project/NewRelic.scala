import sbt._
import sbt.Keys._
import com.ivantopo.sbt.newrelic.SbtNewrelic
import com.ivantopo.sbt.newrelic.SbtNewrelic.newrelic
import com.ivantopo.sbt.newrelic.SbtNewrelic.SbtNewrelicKeys._


object NewRelic {

  lazy val newrelicSettings =  SbtNewrelic.newrelicSettings ++ Seq(
             javaOptions in run   <++=  jvmOptions in newrelic,
                    fork in run     :=  true,
         configFile in newrelic     := file(System.getProperty("user.home") + "/.newrelic/kamon_playground.yml"),
    newrelicVersion in newrelic     :=  "3.1.0"
  )
}
