import sbt._
import sbt.Keys._

import com.typesafe.sbt.SbtAspectj._

aspectjSettings

name := "kamon-newrelic-example"
 
version := "1.0"
 
scalaVersion := "2.10.2"

resolvers += "Kamon repo" at "http://repo.kamon.io"

resolvers += "spray repo" at "http://repo.spray.io"

libraryDependencies += "kamon" %%  "kamon-core" % "0.0.12"

libraryDependencies += "kamon" %%  "kamon-spray" % "0.0.12"

libraryDependencies += "kamon" %%  "kamon-newrelic" % "0.0.12"

libraryDependencies += "io.spray" %  "spray-can"  % "1.2.0"

javaOptions <++= AspectjKeys.weaverOptions in Aspectj
