import sbt.Keys._
import com.ivantopo.sbt.newrelic.SbtNewrelic
import com.ivantopo.sbt.newrelic.SbtNewrelic.newrelic
import com.ivantopo.sbt.newrelic.SbtNewrelic.SbtNewrelicKeys._


object NewRelic {

  lazy val newrelicSettings =  SbtNewrelic.newrelicSettings ++ Seq(
             javaOptions in run   <++=  jvmOptions in newrelic,
    newrelicVersion in newrelic     :=  "2.20.0"
  )
}
